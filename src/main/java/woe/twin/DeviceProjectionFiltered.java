package woe.twin;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
import woe.twin.Device.DeviceEvent;

class DeviceProjectionFiltered {
  static class DeviceEventHandler extends JdbcHandler<List<EventEnvelope<Device.Event>>, DbSession> {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final int zoom;
    private final int shardCount;
    private final int shardId;
    private final String zoomShardId;

    DeviceEventHandler(int zoom, int shardCount, int shardId) {
      this.zoom = zoom;
      this.shardCount = shardCount;
      this.shardId = shardId;
      zoomShardId = String.format("%d-%d", zoom, shardId);
    }

    @Override
    public void process(DbSession session, List<EventEnvelope<Device.Event>> eventEnvelopes) throws Exception {
      var eventsFiltered = eventEnvelopes.stream()
        .map(e -> (DeviceEvent) e.event())
        .filter(e -> isInShard(e))
        .collect(Collectors.toList());
      processFiltered(session, eventsFiltered);
    }

    private void processFiltered(DbSession session, List<DeviceEvent> events) {
      var start = System.nanoTime();
      var connection = session.connection;

      try (var statement = connection.createStatement()) {
        var sql = sql(summarize(events, zoom));
        log.info("zoom {}, shardId {} {}", zoom, shardId, sql);
        statement.executeUpdate(sql);
      } catch (SQLException e) {
        log.error(zoomShardId, e);
        throw new RuntimeException(String.format("Event handler failure %s", zoomShardId));
      }

      log.debug("{} processed {}, {}ns", zoomShardId, events.size(), String.format("%,d", System.nanoTime() - start));
    }

    private List<RegionSummary> summarize(List<DeviceEvent> eventEnvelopes, int zoom) {
      final RegionSummaries regionSummaries = new RegionSummaries(zoom);

      eventEnvelopes.forEach(event-> regionSummaries.add(event));

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

    private boolean isInShard(DeviceEvent deviceEvent) {
      return WorldMap.entityIdOf(WorldMap.regionAtLatLng(zoom, WorldMap.atCenter(deviceEvent.region))).hashCode() % shardCount == shardId;
    }

    @Override
    public void start() {
      log.debug("Start zoom {}, shard {}", zoom, shardId);
      super.start();
    }

    @Override
    public void stop() {
      log.debug("Stop zoom {}, shard {}", zoom, shardId);
      super.stop();
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
    final var shards = actorSystem.settings().config().getInt("woe-twin.projection.shards");
    final var dbSessionFactory = new DbSessionFactory(actorSystem);
    final var tags = Device.tagsAll(actorSystem);

    IntStream.rangeClosed(3, 18).forEach(zoom -> start(actorSystem, dbSessionFactory, zoom, shards, tags.get(0)));
  }

  private static void start(ActorSystem<?> actorSystem, DbSessionFactory dbSessionFactory, int zoom, int shards, String tag) {
    ShardedDaemonProcess.get(actorSystem).init(
      ProjectionBehavior.Command.class,
      "region-summary",
      shards,
      shardId -> ProjectionBehavior.create(create(actorSystem, dbSessionFactory, zoom, shards, shardId, tag)),
      ShardedDaemonProcessSettings.create(actorSystem),
      Optional.of(ProjectionBehavior.stopMessage())
    );
  }

  private static GroupedProjection<Offset, EventEnvelope<Device.Event>>
      create(ActorSystem<?> actorSystem, DbSessionFactory dbSessionFactory, int zoom, int shards, int shardId, String tag) {
    final int groupAfterEnvelopes = actorSystem.settings().config().getInt("woe.twin.projection.group-after-envelopes");
    final Duration groupAfterDuration = actorSystem.settings().config().getDuration("woe.twin.projection.group-after-duration");
    final SourceProvider<Offset, EventEnvelope<Device.Event>> sourceProvider =
      EventSourcedProvider.eventsByTag(actorSystem, JdbcReadJournal.Identifier(), tag);

    return JdbcProjection.groupedWithin(
      ProjectionId.of("region-zoom-summary", String.format("zoom-%d-shard-%d", zoom, shardId)),
      sourceProvider,
      dbSessionFactory::newInstance,
      () -> new DeviceEventHandler(zoom, shards, shardId),
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