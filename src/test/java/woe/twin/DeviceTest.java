package woe.twin;

import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.cluster.Cluster;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
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

  @Test
  public void serializerDeserializeTelemetryCommands() throws IOException {
    CBORFactory cborFactory = new CBORFactory();
    ObjectMapper objectMapper = new ObjectMapper(cborFactory);

    final Device.TelemetryCommand telemetryCreateCommand = new Device.TelemetryCreateCommand(regionForZoom0());
    final byte[] cborTelemetryCreate = objectMapper.writeValueAsBytes(telemetryCreateCommand);
    final Device.TelemetryCommand telemetryCreateCommand1 = objectMapper.readValue(cborTelemetryCreate, Device.TelemetryCreateCommand.class);
    assertEquals(telemetryCreateCommand, telemetryCreateCommand1);

    final Device.TelemetryCommand telemetryDeleteCommand = new Device.TelemetryDeleteCommand(regionForZoom0());
    final byte[] cborTelemetryDelete = objectMapper.writeValueAsBytes(telemetryDeleteCommand);
    final Device.TelemetryCommand telemetryDeleteCommand1 = objectMapper.readValue(cborTelemetryDelete, Device.TelemetryDeleteCommand.class);
    assertEquals(telemetryDeleteCommand, telemetryDeleteCommand1);

    final Device.TelemetryCommand telemetryHappyCommand = new Device.TelemetryHappyCommand(regionForZoom0());
    final byte[] cborTelemetryHappy = objectMapper.writeValueAsBytes(telemetryHappyCommand);
    final Device.TelemetryCommand telemetryHappyCommand1 = objectMapper.readValue(cborTelemetryHappy, Device.TelemetryHappyCommand.class);
    assertEquals(telemetryHappyCommand, telemetryHappyCommand1);

    final Device.TelemetryCommand telemetrySadCommand = new Device.TelemetrySadCommand(regionForZoom0());
    final byte[] cborTelemetrySad = objectMapper.writeValueAsBytes(telemetrySadCommand);
    final Device.TelemetryCommand telemetrySadCommand1 = objectMapper.readValue(cborTelemetrySad, Device.TelemetrySadCommand.class);
    assertEquals(telemetrySadCommand, telemetrySadCommand1);

    final Device.TelemetryCommand telemetryPingCommand = new Device.TelemetryPingCommand(regionForZoom0());
    final byte[] cborTelemetryPing = objectMapper.writeValueAsBytes(telemetryPingCommand);
    final Device.TelemetryCommand telemetryPingCommand1 = objectMapper.readValue(cborTelemetryPing, Device.TelemetryPingCommand.class);
    assertEquals(telemetryPingCommand, telemetryPingCommand1);
  }
}
