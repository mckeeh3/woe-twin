package oti.twin;

import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.cluster.Cluster;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.projection.testkit.javadsl.ProjectionTestKit;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static oti.twin.WorldMap.entityIdOf;
import static oti.twin.WorldMap.regionAtLatLng;

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
      statement.execute("create index iot_twin.region_zoom on iot_twin.region (zoom)");
      statement.execute("create index region_top_left_lat on iot_twin.region (top_left_lat)");
      statement.execute("create index region_top_left_lng on iot_twin.region (top_left_lng)");
      statement.execute("create index region_bot_right_lat on iot_twin.region (bot_right_lat)");
      statement.execute("create index region_bot_right_lng on iot_twin.region (bot_right_lng)");
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
