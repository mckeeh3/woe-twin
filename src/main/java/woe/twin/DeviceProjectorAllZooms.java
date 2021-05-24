package woe.twin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings;
import akka.cluster.sharding.typed.javadsl.ShardedDaemonProcess;
import akka.japi.function.Function;
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal;
import akka.persistence.query.Offset;
import akka.projection.ProjectionBehavior;
import akka.projection.ProjectionId;
import akka.projection.eventsourced.EventEnvelope;
import akka.projection.eventsourced.javadsl.EventSourcedProvider;
import akka.projection.javadsl.GroupedProjection;
import akka.projection.javadsl.SourceProvider;
import akka.projection.jdbc.JdbcSession;
import akka.projection.jdbc.javadsl.JdbcHandler;
import akka.projection.jdbc.javadsl.JdbcProjection;

class DeviceProjectorAllZooms {
  static class DeviceEventHandler extends JdbcHandler<List<EventEnvelope<Device.Event>>, DbSession> {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String tag;

    DeviceEventHandler(String tag) {
      this.tag = tag;

      log.debug("Initialized {}", tag);
    }

    @Override
    public void process(DbSession session, List<EventEnvelope<Device.Event>> eventEnvelopes) {
      //lockTable(session);
      IntStream.rangeClosed(3, 18).forEach(zoom -> process(session, eventEnvelopes, zoom));
    }

    private void process(DbSession session, List<EventEnvelope<Device.Event>> eventEnvelopes, int zoom) {
      final long start = System.nanoTime();
      final Connection connection = session.connection;

      try (Statement statement = connection.createStatement()) {
        final String sql = sql(summarize(eventEnvelopes, zoom));
        log.info("{} {}", tag, sql);
        statement.executeUpdate(sql);
      } catch (SQLException e) {
        log.error(tag, e);
        throw new RuntimeException(String.format("Event handler failure %s", tag), e);
      }

      log.debug("{} processed {}, {}ns", tag, eventEnvelopes.size(), String.format("%,d", System.nanoTime() - start));
    }

    @Override
    public void start() {
      log.debug("Start {}", tag);
      super.start();
    }

    @Override
    public void stop() {
      log.debug("Stop {}", tag);
      super.stop();
    }

    private List<RegionSummary> summarize(List<EventEnvelope<Device.Event>> eventEnvelopes, int zoom) {
      final RegionSummaries regionSummaries = new RegionSummaries(zoom);

      eventEnvelopes.forEach(eventEventEnvelope -> regionSummaries.add(eventEventEnvelope.event()));

      return regionSummaries.asList();
    }

    static String sql(List<RegionSummary> regionSummaries) {
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
      final String dbUrl = actorSystem.settings().config().getString("woe.twin.sql.url");
      final String username = actorSystem.settings().config().getString("woe.twin.sql.username");
      final String password = actorSystem.settings().config().getString("woe.twin.sql.password");
      final int maxPoolSize = actorSystem.settings().config().getInt("woe.twin.sql.max-pool-size");

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

  static void start(ActorSystem<?> actorSystem) {
    final var dbSessionFactory = new DeviceProjectorAllZooms.DbSessionFactory(actorSystem);
    final var tags = Device.tagsAll(actorSystem);

    ShardedDaemonProcess.get(actorSystem).init(
        ProjectionBehavior.Command.class,
        "region-summary",
        tags.size(),
        id -> ProjectionBehavior.create(DeviceProjectorAllZooms.start(actorSystem, dbSessionFactory, tags.get(id))),
        ShardedDaemonProcessSettings.create(actorSystem),
        Optional.of(ProjectionBehavior.stopMessage())
    );
  }

  private static GroupedProjection<Offset, EventEnvelope<Device.Event>>
      start(ActorSystem<?> actorSystem, DbSessionFactory dbSessionFactory, String tag) {
    final int groupAfterEnvelopes = actorSystem.settings().config().getInt("woe.twin.projection.group-after-envelopes");
    final Duration groupAfterDuration = actorSystem.settings().config().getDuration("woe.twin.projection.group-after-duration");
    final SourceProvider<Offset, EventEnvelope<Device.Event>> sourceProvider =
        //EventSourcedProvider.eventsByTag(actorSystem, CassandraReadJournal.Identifier(), tag);
        EventSourcedProvider.eventsByTag(actorSystem, JdbcReadJournal.Identifier(), tag);
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

    RegionSummary(WorldMap.Region region) {
      this(region, 0, 0, 0);
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
    private final int zoom;
    private final Map<WorldMap.Region, RegionSummary> regionSummaries = new HashMap<>();

    RegionSummaries(int zoom) {
      this.zoom = zoom;
    }

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
      regionSummaries.compute(eventToZoomRegion(event), (region, regionSummary) ->
          regionSummary == null ? (new RegionSummary(region)).activated() : regionSummary.activated());
    }

    private void deactivatedHappy(Device.DeviceDeactivatedHappy event) {
      regionSummaries.compute(eventToZoomRegion(event), (region, regionSummary) ->
          regionSummary == null ? (new RegionSummary(region)).deactivatedHappy() : regionSummary.deactivatedHappy());
    }

    private void deactivatedSad(Device.DeviceDeactivatedSad event) {
      regionSummaries.compute(eventToZoomRegion(event), (region, regionSummary) ->
          regionSummary == null ? (new RegionSummary(region)).deactivatedSad() : regionSummary.deactivatedSad());
    }

    private void madeHappy(Device.DeviceMadeHappy event) {
      regionSummaries.compute(eventToZoomRegion(event), (region, regionSummary) ->
          regionSummary == null ? (new RegionSummary(region)).madeHappy() : regionSummary.madeHappy());
    }

    private void madeSad(Device.DeviceMadeSad event) {
      regionSummaries.compute(eventToZoomRegion(event), (region, regionSummary) ->
          regionSummary == null ? (new RegionSummary(region)).madeSad() : regionSummary.madeSad());
    }

    List<RegionSummary> asList() {
      return new ArrayList<>(regionSummaries.values());
    }

    private WorldMap.Region eventToZoomRegion(Device.DeviceEvent event) {
      return WorldMap.regionAtLatLng(zoom, WorldMap.atCenter(event.region));
    }
  }
}
