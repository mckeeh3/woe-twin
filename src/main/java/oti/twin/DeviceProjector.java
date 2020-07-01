package oti.twin;

import akka.Done;
import akka.actor.typed.ActorSystem;
import akka.japi.function.Function;
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal;
import akka.persistence.query.Offset;
import akka.projection.ProjectionId;
import akka.projection.eventsourced.EventEnvelope;
import akka.projection.eventsourced.javadsl.EventSourcedProvider;
import akka.projection.javadsl.GroupedProjection;
import akka.projection.javadsl.SourceProvider;
import akka.projection.jdbc.JdbcSession;
import akka.projection.jdbc.javadsl.JdbcHandler;
import akka.projection.jdbc.javadsl.JdbcProjection;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;

class DeviceProjector {
  static class DeviceEventHandler extends JdbcHandler<List<EventEnvelope<Device.Event>>, DbSession> {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String tag;
    private final int zoom;

    DeviceEventHandler(String tag) {
      this.tag = tag;
      this.zoom = tagToZoom(tag);
      log.debug("Initialized {}", tag);
    }

    @Override
    public void process(DbSession session, List<EventEnvelope<Device.Event>> eventEnvelopes) {
      final Connection connection = session.connection;

      final long start = System.nanoTime();
      eventEnvelopes.forEach(eventEventEnvelope -> {
        final Device.Event event = eventEventEnvelope.event();

        try {
          if (event instanceof Device.DeviceActivated) {
            update(connection, (Device.DeviceActivated) event);
          } else if (event instanceof Device.DeviceDeactivatedHappy) {
            update(connection, (Device.DeviceDeactivatedHappy) event);
          } else if (event instanceof Device.DeviceDeactivatedSad) {
            update(connection, (Device.DeviceDeactivatedSad) event);
          } else if (event instanceof Device.DeviceMadeHappy) {
            update(connection, (Device.DeviceMadeHappy) event);
          } else if (event instanceof Device.DeviceMadeSad) {
            update(connection, (Device.DeviceMadeSad) event);
          }
        } catch (SQLException e) {
          log.error(String.format("%s", tag), e);
          throw new RuntimeException(String.format("%s", tag), e);
        }
      });
      log.debug("{} processed {}, {}ns", tag, eventEnvelopes.size(), String.format("%,d", System.nanoTime() - start));
    }

    @Override
    public CompletionStage<Done> start() {
      log.debug("Start {}", tag);
      return super.start();
    }

    @Override
    public CompletionStage<Done> stop() {
      log.debug("Stop {}", tag);
      return super.stop();
    }

    private void update(Connection connection, Device.DeviceActivated event) throws SQLException {
      update(connection, read(connection, eventToZoomRegion(zoom, event)).activated());
    }

    private void update(Connection connection, Device.DeviceDeactivatedHappy event) throws SQLException {
      update(connection, read(connection, eventToZoomRegion(zoom, event)).deactivatedHappy());
    }

    private void update(Connection connection, Device.DeviceDeactivatedSad event) throws SQLException {
      update(connection, read(connection, eventToZoomRegion(zoom, event)).deactivatedSad());
    }

    private void update(Connection connection, Device.DeviceMadeHappy event) throws SQLException {
      update(connection, read(connection, eventToZoomRegion(zoom, event)).madeHappy());
    }

    private void update(Connection connection, Device.DeviceMadeSad event) throws SQLException {
      update(connection, read(connection, eventToZoomRegion(zoom, event)).madeSad());
    }

    private RegionSummary read(Connection connection, WorldMap.Region region) throws SQLException {
      try (Statement statement = connection.createStatement()) {
        String sql = String.format("select * from region"
                + " where zoom = %d"
                + " and top_left_lat = %1.9f"
                + " and top_left_lng = %1.9f"
                + " and bot_right_lat = %1.9f"
                + " and bot_right_lng = %1.9f",
            region.zoom, region.topLeft.lat, region.topLeft.lng, region.botRight.lat, region.botRight.lng);
        final ResultSet resultSet = statement.executeQuery(sql);
        if (resultSet.next()) {
          return new RegionSummary(region, resultSet.getInt("device_count"), resultSet.getInt("happy_count"), resultSet.getInt("sad_count"));
        } else {
          return new RegionSummary(region, 0, 0, 0);
        }
      }
    }

    private void update(Connection connection, RegionSummary regionSummary) throws SQLException {
      String sql = regionSummary.deviceCount > 0
          ? insertOrUpdate(regionSummary)
          : delete(regionSummary);
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate(sql);
      }
    }

    private String insertOrUpdate(RegionSummary regionSummary) {
      final WorldMap.Region region = regionSummary.region;
      return String.format("insert into region"
              + " (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng, device_count, happy_count, sad_count)"
              + " values (%d, %1.9f, %1.9f, %1.9f, %1.9f, %d, %d, %d)"
              + " on conflict on constraint region_pkey"
              + " do update set"
              + " device_count = %d,"
              + " happy_count = %d,"
              + " sad_count = %d",
          region.zoom, region.topLeft.lat, region.topLeft.lng, region.botRight.lat, region.botRight.lng,
          regionSummary.deviceCount, regionSummary.happyCount, regionSummary.sadCount, // for insert
          regionSummary.deviceCount, regionSummary.happyCount, regionSummary.sadCount); // for update
    }

    private String delete(RegionSummary regionSummary) {
      final WorldMap.Region region = regionSummary.region;
      return String.format("delete from region"
              + " where zoom = %d"
              + " and top_left_lat = %1.9f"
              + " and top_left_lng = %1.9f"
              + " and bot_right_lat = %1.9f"
              + " and bot_right_lng = %1.9f",
          region.zoom, region.topLeft.lat, region.topLeft.lng, region.botRight.lat, region.botRight.lng);
    }

    private static int tagToZoom(String tag) {
      return Integer.parseInt(tag.split("-")[1]);
    }

    private static WorldMap.Region eventToZoomRegion(int zoom, Device.DeviceEvent event) {
      return WorldMap.regionAtLatLng(zoom, WorldMap.atCenter(event.region));
    }
  }

  static class DbSession implements JdbcSession {
    final Connection connection;

    DbSession(DataSource dataSource) {
      try {
        connection = dataSource.getConnection();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public <Result> Result withConnection(Function<Connection, Result> func) throws Exception {
      return func.apply(connection);
    }

    @Override
    public void commit() throws SQLException {
      connection.commit();
    }

    @Override
    public void rollback() throws SQLException {
      connection.rollback();
    }

    @Override
    public void close() throws SQLException {
      connection.close();
    }
  }

  static class DbSessionFactory {
    private final DataSource dataSource;

    DbSessionFactory(ActorSystem<?> actorSystem) {
      final String dbUrl = actorSystem.settings().config().getString("oti.twin.sql.url");
      final String username = actorSystem.settings().config().getString("oti.twin.sql.username");
      final String password = actorSystem.settings().config().getString("oti.twin.sql.password");
      final int maxPoolSize = actorSystem.settings().config().getInt("oti.twin.sql.max-pool-size");

      final HikariConfig config = new HikariConfig();
      config.setJdbcUrl(dbUrl);
      config.setUsername(username);
      config.setPassword(password);
      config.setMaximumPoolSize(maxPoolSize);
      config.setAutoCommit(false);

      dataSource = new HikariDataSource(config);
      actorSystem.log().debug("Datasource {}, pool size {}", dbUrl, maxPoolSize);
    }

    DbSession newInstance() {
      return new DbSession(dataSource);
    }
  }

  static GroupedProjection<Offset, EventEnvelope<Device.Event>> start(ActorSystem<?> actorSystem, DbSessionFactory dbSessionFactory, String tag) {
    final int groupAfterEnvelopes = actorSystem.settings().config().getInt("oti.twin.projection.group-after-envelopes");
    final Duration groupAfterDuration = actorSystem.settings().config().getDuration("oti.twin.projection.group-after-duration");
    final SourceProvider<Offset, EventEnvelope<Device.Event>> sourceProvider =
        EventSourcedProvider.eventsByTag(actorSystem, CassandraReadJournal.Identifier(), tag);
    return JdbcProjection.groupedWithin(
        ProjectionId.of("region", tag),
        sourceProvider,
        dbSessionFactory::newInstance,
        () -> new DeviceEventHandler(tag),
        actorSystem
    ).withGroup(groupAfterEnvelopes, groupAfterDuration);
  }

  public static class RegionSummary {
    public final WorldMap.Region region;
    public final int deviceCount;
    public final int happyCount;
    public final int sadCount;

    public RegionSummary(WorldMap.Region region, int deviceCount, int happyCount, int sadCount) {
      this.region = region;
      this.deviceCount = deviceCount;
      this.happyCount = happyCount;
      this.sadCount = sadCount;
    }

    @Override
    public String toString() {
      return String.format("%s[%s, %d, %d, %d]", getClass().getSimpleName(), region, deviceCount, happyCount, sadCount);
    }

    RegionSummary activated() {
      return new RegionSummary(region, deviceCount + 1, happyCount + 1, sadCount);
    }

    RegionSummary deactivatedHappy() {
      return new RegionSummary(region, Math.max(0, deviceCount - 1), Math.max(0, happyCount - 1), sadCount);
    }

    RegionSummary deactivatedSad() {
      return new RegionSummary(region, Math.max(0, deviceCount - 1), happyCount, Math.max(0, sadCount - 1));
    }

    RegionSummary madeHappy() {
      return new RegionSummary(region, deviceCount, Math.min(deviceCount, happyCount + 1), Math.max(0, sadCount - 1));
    }

    RegionSummary madeSad() {
      return new RegionSummary(region, deviceCount, Math.max(0, happyCount - 1), Math.min(deviceCount, sadCount + 1));
    }
  }
}
