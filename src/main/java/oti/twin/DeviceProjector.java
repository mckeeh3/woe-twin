package oti.twin;

import akka.Done;
import akka.actor.typed.ActorSystem;
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal;
import akka.persistence.query.Offset;
import akka.projection.ProjectionId;
import akka.projection.cassandra.javadsl.CassandraProjection;
import akka.projection.cassandra.javadsl.GroupedCassandraProjection;
import akka.projection.eventsourced.EventEnvelope;
import akka.projection.eventsourced.javadsl.EventSourcedProvider;
import akka.projection.javadsl.Handler;
import akka.projection.javadsl.SourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

class DeviceProjector {
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
      return new RegionSummary(region, deviceCount - 1, happyCount - 1, sadCount);
    }

    RegionSummary deactivatedSad() {
      return new RegionSummary(region, deviceCount - 1, happyCount, sadCount - 1);
    }

    RegionSummary madeHappy() {
      return new RegionSummary(region, deviceCount, happyCount + 1, sadCount - 1);
    }

    RegionSummary madeSad() {
      return new RegionSummary(region, deviceCount, happyCount - 1, sadCount + 1);
    }
  }

  static class DeviceEventHandler extends Handler<List<EventEnvelope<Device.Event>>> {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String tag;
    private final int zoom;
    private final String dbUrl;
    private final String username;
    private final String password;

    DeviceEventHandler(String tag, String dbUrl, String username, String password) {
      this.tag = tag;
      this.zoom = tagToZoom(tag);
      this.dbUrl = dbUrl;
      this.username = username;
      this.password = password;
    }

    @Override
    public CompletionStage<Done> start() {
      return super.start();
    }

    @Override
    public CompletionStage<Done> stop() {
      return super.stop();
    }

    @Override
    public CompletionStage<Done> process(List<EventEnvelope<Device.Event>> eventEnvelopes) {

      try {
        final Connection connection = begin(dbUrl, username, password);
        eventEnvelopes.forEach(eventEventEnvelope -> {
          final Device.Event event = eventEventEnvelope.event();
          log.debug("{} {}", tag, event);

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
          }
        });
        commit(connection);
      } catch (SQLException e) {
        log.error(String.format("%s", tag), e);
      }

      return CompletableFuture.completedFuture(Done.getInstance());
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

    private Connection begin(String dbUrl, String username, String password) throws SQLException {
      // TODO this should be replaced when transactions are handled by Akka Projection
      log.debug("{} - BEGIN transaction", tag);
      final Connection connection = DriverManager.getConnection(dbUrl, username, password);
      try (Statement statement = connection.createStatement()) {
        statement.execute("begin transaction isolation level repeatable read");
        return connection;
      }
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
        log.debug("{} - {}", tag, sql);
        final ResultSet resultSet = statement.executeQuery(sql);
        if (resultSet.next()) {
          return new RegionSummary(region, resultSet.getInt("device_count"), resultSet.getInt("happy_count"), resultSet.getInt("sad_count"));
        } else {
          return new RegionSummary(region, 0, 0, 0);
        }
      }
    }

    private void update(Connection connection, RegionSummary regionSummary) throws SQLException {
      final WorldMap.Region region = regionSummary.region;
      try (Statement statement = connection.createStatement()) {
        String sql = String.format("insert into region"
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
        log.debug("{} - {}", tag, sql);
        statement.executeUpdate(sql);
      }
    }

    private void commit(Connection connection) throws SQLException {
      // TODO this should be replaced when transactions are handled by Akka Projection
      log.debug("{} - COMMIT transaction", tag);
      try (Statement statement = connection.createStatement()) {
        statement.execute("commit");
        connection.close();
      }
    }

    private static int tagToZoom(String tag) {
      return Integer.parseInt(tag.split("-")[1]);
    }

    private static WorldMap.Region eventToZoomRegion(int zoom, Device.DeviceEvent event) {
      return WorldMap.regionAtLatLng(zoom, WorldMap.atCenter(event.region));
    }
  }

  static GroupedCassandraProjection<EventEnvelope<Device.Event>> start(ActorSystem<?> actorSystem, String tag) {
    final String dbUrl = actorSystem.settings().config().getString("oti.twin.sql.url");
    final String username = actorSystem.settings().config().getString("oti.twin.sql.username");
    final String password = actorSystem.settings().config().getString("oti.twin.sql.password");

    final SourceProvider<Offset, EventEnvelope<Device.Event>> sourceProvider =
        EventSourcedProvider.eventsByTag(actorSystem, CassandraReadJournal.Identifier(), tag);
    return CassandraProjection.groupedWithin(
        ProjectionId.of("region", tag),
        sourceProvider,
        new DeviceEventHandler(tag, dbUrl, username, password)
    ).withGroup(100, Duration.ofSeconds(1)); // TODO config these settings?
  }
}
