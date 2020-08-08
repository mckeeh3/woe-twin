package woe.twin;

import akka.actor.testkit.typed.javadsl.*;
import akka.cluster.Cluster;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static woe.twin.WorldMap.*;

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

    LoggingTestKit.info("TelemetryCreateCommand[" + region)
        .expect(
            testKit.system(),
            () -> {
              entityRef.tell(new Device.TelemetryCreateCommand(region, null));
              return null;
            }
        );
  }

  @Test
  public void tagsForCreatesTagsForZoom3to18() {
    final WorldMap.Region region = regionAtLatLng(18, latLng(51.5007541, -0.11688530));
    final Set<String> tags = Device.tagsFor(region, 100);

    assertEquals(1, tags.size());
  }

  @Test
  public void tagsAllBasedOnConfigSettings() {
    final int numberOfShards = testKit.system().settings().config().getInt(Device.projectionShards);
    final List<String> tags = Device.tagsAll(testKit.system());

    assertEquals(numberOfShards, tags.size());
    assertTrue(tags.stream().anyMatch(t -> t.equals("0")));
    assertTrue(tags.stream().anyMatch(t -> t.startsWith("" + (numberOfShards - 1))));
  }

  @Test
  public void serializerDeserializeTelemetryCommands() throws IOException {
    final SerializationTestKit serializationTestKit = ActorTestKit.create(testKit.system()).serializationTestKit();
    final TestProbe<Device.TelemetryResponse> probe = testKit.createTestProbe();

    final Device.TelemetryCommand telemetryCreateCommand = new Device.TelemetryCreateCommand(regionForZoom0(), probe.ref());
    serializationTestKit.verifySerialization(telemetryCreateCommand, true);

    final Device.TelemetryCommand telemetryDeleteCommand = new Device.TelemetryDeleteCommand(regionForZoom0(), probe.ref());
    serializationTestKit.verifySerialization(telemetryDeleteCommand, true);

    final Device.TelemetryCommand telemetryHappyCommand = new Device.TelemetryHappyCommand(regionForZoom0(), probe.ref());
    serializationTestKit.verifySerialization(telemetryHappyCommand, true);

    final Device.TelemetryCommand telemetrySadCommand = new Device.TelemetrySadCommand(regionForZoom0(), probe.ref());
    serializationTestKit.verifySerialization(telemetrySadCommand, true);

    final Device.TelemetryCommand telemetryPingCommand = new Device.TelemetryPingCommand(regionForZoom0(), probe.ref());
    serializationTestKit.verifySerialization(telemetryPingCommand, true);
  }

  @Test
  public void serializationDeserializationTelemetryResponse() {
    final SerializationTestKit serializationTestKit = ActorTestKit.create(testKit.system()).serializationTestKit();
    final TestProbe<Device.TelemetryResponse> probe = testKit.createTestProbe();

    final Device.TelemetryCreateCommand telemetryCreateCommand = new Device.TelemetryCreateCommand(regionForZoom0(), probe.ref());
    serializationTestKit.verifySerialization(new Device.TelemetryCreateResponse(telemetryCreateCommand), true);

    final Device.TelemetryDeleteCommand telemetryDeleteCommand = new Device.TelemetryDeleteCommand(regionForZoom0(), probe.ref());
    serializationTestKit.verifySerialization(new Device.TelemetryDeleteResponse(telemetryDeleteCommand), true);

    final Device.TelemetryHappyCommand telemetryHappyCommand = new Device.TelemetryHappyCommand(regionForZoom0(), probe.ref());
    serializationTestKit.verifySerialization(new Device.TelemetryHappyResponse(telemetryHappyCommand), true);

    final Device.TelemetrySadCommand telemetrySadCommand = new Device.TelemetrySadCommand(regionForZoom0(), probe.ref());
    serializationTestKit.verifySerialization(new Device.TelemetrySadResponse(telemetrySadCommand), true);

    final Device.TelemetryPingCommand telemetryPingCommand = new Device.TelemetryPingCommand(regionForZoom0(), probe.ref());
    serializationTestKit.verifySerialization(new Device.TelemetryPingResponse(telemetryPingCommand), true);
  }
}
