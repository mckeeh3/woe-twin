package oti.twin;

import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.cluster.Cluster;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static oti.twin.WorldMap.*;
import static org.junit.jupiter.api.Assertions.*;

public class DeviceTest {
  private static ClusterSharding clusterSharding;

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource(config());

  private static Config config() {
    return ConfigFactory.parseString(
        String.format("akka.cluster.seed-nodes = [ \"akka://%s@127.0.0.1:25520\" ] %n", DeviceTest.class.getSimpleName())
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

  @Test
  public void createDevice() {
    // London across Westminster Bridge at Park Plaza Hotel
    WorldMap.Region region = regionAtLatLng(18, new LatLng(51.50079211, -0.11682093));

    final EntityRef<Device.Command> entityRef = clusterSharding.entityRefFor(Device.entityTypeKey, entityIdOf(region));

    LoggingTestKit.debug("create " + region)
        .expect(
            testKit.system(),
            () -> {
              entityRef.tell(new Device.TelemetryCreateCommand(region));
              return null;
            }
        );
  }

  @Test
  public void tagsForCreatesTagsForZoom3to18() {
    final WorldMap.Region region = regionAtLatLng(18, latLng(51.5007541, -0.11688530));
    final Set<String> tags = Device.tagsFor(region, 100);

    assertEquals(16, tags.size());
    assertTrue(tags.stream().anyMatch(t -> t.startsWith("zoom-3-tag-")));
    assertTrue(tags.stream().anyMatch(t -> t.startsWith("zoom-18-tag-")));
  }

  @Test
  public void tagsAllBasedOnConfigSettings() {
    final int numberOfShards = testKit.system().settings().config().getInt(Device.projectionShardsPerZoom);
    final List<String> tags = Device.tagsAll(testKit.system());

    assertEquals(numberOfShards * 16, tags.size());
    assertTrue(tags.stream().anyMatch(t -> t.equals("zoom-3-tag-0")));
    assertTrue(tags.stream().anyMatch(t -> t.startsWith("zoom-18-tag-" + (numberOfShards - 1))));
  }
}
