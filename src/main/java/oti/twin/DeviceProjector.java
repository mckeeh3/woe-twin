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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
      final long start = System.nanoTime();
      final Connection connection = session.connection;

      try (Statement statement = connection.createStatement()) {
        log.info("{} {}", tag, sql(summarize(eventEnvelopes)));
        statement.executeUpdate(sql(summarize(eventEnvelopes)));
      } catch (SQLException e) {
        log.error(tag, e);
        throw new RuntimeException(String.format("Event handler failure %s", tag), e);
      }

      log.debug("{} processed {}, {}ns", tag, eventEnvelopes.size(), String.format("%,d", System.nanoTime() - start));
    }

    //@Override
    public void processOLD(DbSession session, List<EventEnvelope<Device.Event>> eventEnvelopes) {
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

    private List<RegionSummary> summarize(List<EventEnvelope<Device.Event>> eventEnvelopes) {
      final RegionSummaries regionSummaries = new RegionSummaries();

      eventEnvelopes.forEach(eventEventEnvelope -> {
        final Device.Event event = eventEventEnvelope.event();
        regionSummaries.add(eventEventEnvelope.event());
      });

      return regionSummaries.asList();
    }

    private String sql(List<RegionSummary> regionSummaries) {
      final StringBuilder sql = new StringBuilder();
      String delimiter = "";

      sql.append("insert into region");
      sql.append(" (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng, device_count, happy_count, sad_count)");
      sql.append(" values");

      for (RegionSummary regionSummary : regionSummaries) {
        final WorldMap.Region region = regionSummary.region;
        sql.append(delimiter);
        sql.append(String.format("%n (%d, %1.9f, %1.9f, %1.9f, %1.9f, %d, %d, %d)",
            region.zoom, region.topLeft.lat, region.topLeft.lng, region.botRight.lat, region.botRight.lng,
            regionSummary.deviceCount, regionSummary.happyCount, regionSummary.sadCount));
        delimiter = ",";
      }

      sql.append(" on conflict on constraint region_pkey");
      sql.append(" do update set");
      sql.append(" device_count = region.device_count + excluded.device_count,");
      sql.append(" happy_count = region.happy_count + excluded.happy_count,");
      sql.append(" sad_count = region.sad_count + excluded.sad_count");

      return sql.toString();
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
      config.setTransactionIsolation("TRANSACTION_SERIALIZABLE");

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

  static class RegionSummary {
    public final WorldMap.Region region;
    public int deviceCount;
    public int happyCount;
    public int sadCount;

    RegionSummary(WorldMap.Region region, int deviceCount, int happyCount, int sadCount) {
      this.region = region;
      this.deviceCount = deviceCount;
      this.happyCount = happyCount;
      this.sadCount = sadCount;
    }

    RegionSummary(Device.DeviceEvent deviceEvent) {
      this(deviceEvent.region, 0, 0, 0);
    }

    @Override
    public String toString() {
      return String.format("%s[%s, %d, %d, %d]", getClass().getSimpleName(), region, deviceCount, happyCount, sadCount);
    }

    RegionSummary activated() {
      deviceCount++;
      happyCount++;
      return this;
    }

    RegionSummary deactivatedHappy() {
      deviceCount--;
      happyCount--;
      return this;
    }

    RegionSummary deactivatedSad() {
      deviceCount--;
      sadCount--;
      return this;
    }

    RegionSummary madeHappy() {
      happyCount++;
      sadCount--;
      return this;
    }

    RegionSummary madeSad() {
      happyCount--;
      sadCount++;
      return this;
    }
  }

  static class RegionSummaries {
    private final Map<WorldMap.Region, RegionSummary> regionSummaries = new HashMap<>();

    void add(Device.Event event) {
      if (event instanceof Device.DeviceActivated) {
        activated((Device.DeviceActivated) event);
      } else if (event instanceof Device.DeviceDeactivatedHappy) {
        deactivatedHappy((Device.DeviceDeactivatedHappy) event);
      } else if (event instanceof Device.DeviceDeactivatedSad) {
        deactivatedSad((Device.DeviceDeactivatedSad) event);
      } else if (event instanceof Device.DeviceMadeHappy) {
        madeHappy((Device.DeviceMadeHappy) event);
      } else if (event instanceof Device.DeviceMadeSad) {
        madeSad((Device.DeviceMadeSad) event);
      }
    }

    private void activated(Device.DeviceActivated event) {
      regionSummaries.compute(event.region, (region, regionSummary) ->
          regionSummary == null ? (new RegionSummary(event)).activated() : regionSummary.activated());
    }

    private void deactivatedHappy(Device.DeviceDeactivatedHappy event) {
      regionSummaries.compute(event.region, (region, regionSummary) ->
          regionSummary == null ? (new RegionSummary(event)).deactivatedHappy() : regionSummary.deactivatedHappy());
    }

    private void deactivatedSad(Device.DeviceDeactivatedSad event) {
      regionSummaries.compute(event.region, (region, regionSummary) ->
          regionSummary == null ? (new RegionSummary(event)).deactivatedSad() : regionSummary.deactivatedSad());
    }

    private void madeHappy(Device.DeviceMadeHappy event) {
      regionSummaries.compute(event.region, (region, regionSummary) ->
          regionSummary == null ? (new RegionSummary(event)).madeHappy() : regionSummary.madeHappy());
    }

    private void madeSad(Device.DeviceMadeSad event) {
      regionSummaries.compute(event.region, (region, regionSummary) ->
          regionSummary == null ? (new RegionSummary(event)).madeSad() : regionSummary.madeSad());
    }

    List<RegionSummary> asList() {
      return new ArrayList<RegionSummary>(regionSummaries.values());
    }
  }
}
