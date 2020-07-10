package oti.twin;

import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.projection.testkit.javadsl.ProjectionTestKit;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static oti.twin.WorldMap.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DeviceProjectorTest {
  private static ClusterSharding clusterSharding;

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource(config());
  ProjectionTestKit projectionTestKit = ProjectionTestKit.create(testKit.testKit());

  private static Config config() {
    return ConfigFactory.parseString(
        String.format("akka.cluster.seed-nodes = [ \"akka://%s@127.0.0.1:25520\" ] %n", DeviceProjectorTest.class.getSimpleName())
            + String.format("akka.persistence.snapshot-store.local.dir = \"%s-%s\" %n", "target/snapshot", UUID.randomUUID().toString())
    ).withFallback(ConfigFactory.load("application-test.conf"));
  }

  @BeforeClass
  public static void setupClass() {
    clusterSharding = ClusterSharding.get(testKit.system());

    clusterSharding.init(
        Entity.of(
            Device.entityTypeKey,
            entityContext ->
                Device.create(entityContext.getEntityId(), clusterSharding)
        )
    );

    testKit.system().log().info("Test cluster node {}", Cluster.get(testKit.system()).selfMember());
  }

  @Ignore
  @Test
  public void createDevice() {
    // London across Westminster Bridge at Park Plaza Hotel
    WorldMap.Region region = regionAtLatLng(18, new WorldMap.LatLng(51.50079211, -0.11682093));

    final EntityRef<Device.Command> entityRef = clusterSharding.entityRefFor(Device.entityTypeKey, entityIdOf(region));

    LoggingTestKit.debug("create " + region)
        .expect(
            testKit.system(),
            () -> {
              entityRef.tell(new Device.TelemetryCreateCommand(region));
              return null;
            }
        );

    final DeviceProjector.DbSessionFactory dbSessionFactory = new DeviceProjector.DbSessionFactory(testKit.system());
    final RegionSummaryReader regionSummaryReader = new RegionSummaryReader();
    projectionTestKit.run(DeviceProjector.start(testKit.system(), dbSessionFactory, "zoom-3-entity--12"), () ->
        regionSummaryReader.read("No-op")
            .toCompletableFuture().get(1, TimeUnit.SECONDS)
    );
  }

  @Ignore // Found an issue with akka.projection.jdbc.internalJdbcSettings checking config type
  @Test
  public void configValueType() {
    final int fixedPoolSize = testKit.system().settings().config()
        .getInt("akka.projection.jdbc.blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size");
    assertEquals(50, fixedPoolSize);
    final ConfigValue configValue = testKit.system().settings().config()
        .getValue("akka.projection.jdbc.blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size");
    assertEquals(ConfigValueType.NUMBER, configValue.valueType());
    assertNotEquals(ConfigValueType.STRING, configValue.valueType());
  }

  @Ignore
  @Test
  public void loadTest() throws SQLException {
    final int zoom = 8;
    final long devicesPerZoom = Math.round(Math.pow(4, 18 - zoom));
    final DataSource dataSource = dataSource(testKit.system());
    final List<DeviceProjector.RegionSummary> regionSummaries = regionSummaries(regionAtLatLng(zoom, new LatLng(47.15, 2.81)));

    testKit.system().log().info("{} rows created", String.format("%,d", regionSummaries.size()));
    assertTrue(regionSummaries.size() > devicesPerZoom);

    insertUpdate(dataSource, 1000, regionSummaries);
  }

  private static void insertUpdate(DataSource dataSource, int increment, List<DeviceProjector.RegionSummary> regionSummaries) throws SQLException {
    for (int i = 0; i < regionSummaries.size(); i += increment) {
      insertUpdate(dataSource, regionSummaries.subList(i, Math.min(i + increment, regionSummaries.size())));
      testKit.system().log().info("{}", String.format("commit %,d", i + increment));
    }
  }

  private long expectedRegionsForZoom(int zoomSelection, int zoomRegion) {
    return Math.round(Math.pow(4, 18 - zoomSelection) / Math.pow(4, 18 - Math.max(zoomSelection, zoomRegion)));
  }

  private long regionsForZoom(int zoom, List<DeviceProjector.RegionSummary> regionSummaries) {
    return regionSummaries.stream()
        .filter(r -> r.region.zoom == zoom)
        .count();
  }

  private long devicesForZoom(int zoom, List<DeviceProjector.RegionSummary> regionSummaries) {
    return regionSummaries.stream()
        .filter(r -> r.region.zoom == zoom)
        .map(rs -> rs.deviceCount)
        .reduce(0, Integer::sum);
  }

  private static void insertUpdate(DataSource dataSource, List<DeviceProjector.RegionSummary> regionSummaries) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      final String sql = DeviceProjector.DeviceEventHandler.sql(regionSummaries);
      statement.executeUpdate(sql);
      connection.commit();
    }
  }

  private static List<DeviceProjector.RegionSummary> regionSummaries(WorldMap.Region region) {
    List<DeviceProjector.RegionSummary> regionSummaries = new ArrayList<>();
    final Map<Integer, DeviceProjector.RegionSummaries> regionSummariesMap = regionSummariesFor(region);
    regionSummariesMap
        .forEach((zoom, summaries) -> regionSummaries.addAll(summaries.asList()));
    return regionSummaries;
  }

  private static Map<Integer, DeviceProjector.RegionSummaries> regionSummariesFor(WorldMap.Region region) {
    final Map<Integer, DeviceProjector.RegionSummaries> regionSummariesMap = new HashMap<>();
    final List<Device.DeviceActivated> devicesActivated = devicesFor(region);

    IntStream.rangeClosed(3, 18).forEach(zoom -> regionSummariesMap.put(zoom, new DeviceProjector.RegionSummaries(zoom)));
    devicesActivated
        .forEach(device -> IntStream.rangeClosed(3, 18)
            .forEach(zoom -> regionSummariesMap.get(zoom).add(device)));
    return regionSummariesMap;
  }

  private static List<Device.DeviceActivated> devicesFor(WorldMap.Region region) {
    if (region.isDevice()) {
      return Collections.singletonList(new Device.DeviceActivated(region));
    } else {
      List<Device.DeviceActivated> devices = new ArrayList<>();
      subRegionsFor(region)
          .forEach(subRegion -> devices.addAll(devicesFor(subRegion)));
      return devices;
    }
  }

  @BeforeClass
  public static void setup() throws SQLException {
    try (Connection connection = getConnection();
         Statement statement = connection.createStatement()) {
      statement.execute("create schema if not exists iot_twin");
      statement.execute(""
          + "create table if not exists iot_twin.region ("
          + "zoom            integer,"
          + "top_left_lat    double precision,"
          + "top_left_lng    double precision,"
          + "bot_right_lat   double precision,"
          + "bot_right_lng   double precision,"
          + "device_count    integer,"
          + "happy_count     integer,"
          + "sad_count       integer,"
          + "constraint region_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng))");
      //statement.execute("create index iot_twin.region_zoom on iot_twin.region (zoom)");
      //statement.execute("create index region_top_left_lat on iot_twin.region (top_left_lat)");
      //statement.execute("create index region_top_left_lng on iot_twin.region (top_left_lng)");
      //statement.execute("create index region_bot_right_lat on iot_twin.region (bot_right_lat)");
      //statement.execute("create index region_bot_right_lng on iot_twin.region (bot_right_lng)");
    }
  }

  @AfterClass
  public static void tearDown() throws SQLException {
    try (Connection connection = getConnection();
         Statement statement = connection.createStatement()) {
      statement.execute("drop table iot_twin.region");
    }
  }

  private static Connection getConnection() throws SQLException {
    return DriverManager.getConnection("jdbc:hsqldb:mem:test");
  }

  private static DataSource dataSource(ActorSystem<?> actorSystem) {
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

    return new HikariDataSource(config);
  }

  private static void insert(WorldMap.Region region, int deviceCount, int happyCount, int sadCount) throws SQLException {
    try (Connection connection = getConnection();
         Statement statement = connection.createStatement()) {
      String sql = String.format("insert into region"
              + " (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng, device_count, happy_count, sad_count)"
              + " values (%d, %1.9f, %1.9f, %1.9f, %1.9f, %d, %d, %d)"
              //+ " on conflict (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)"
              + " on conflict on constraint region_pkey"
              + " do update set"
              + " device_count = %d,"
              + " happy_count = %d,"
              + " sad_count = %d",
          region.zoom, region.topLeft.lat, region.topLeft.lng, region.botRight.lat, region.botRight.lng,
          deviceCount, happyCount, sadCount, deviceCount, happyCount, sadCount);
      statement.executeUpdate(sql);
    }
  }

  private static class RegionSummaryReader {
    CompletionStage<String> read(String id) {
      assertEquals("TODO", id);
      return null;
    }
  }
}
