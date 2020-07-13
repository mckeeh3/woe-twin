package oti.twin;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.cluster.Cluster;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.Materializer;
import akka.util.ByteString;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.UUID;

import static oti.twin.WorldMap.latLng;
import static oti.twin.WorldMap.regionAtLatLng;
import static org.junit.jupiter.api.Assertions.*;

public class HttpServerTest {
  private static HttpServer httpServer;

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource(config());

  private static Config config() {
    return ConfigFactory.parseString(
        String.format("akka.cluster.seed-nodes = [ \"akka://%s@127.0.0.1:25520\" ] %n", HttpServerTest.class.getSimpleName())
            + String.format("akka.persistence.snapshot-store.local.dir = \"%s-%s\" %n", "target/snapshot", UUID.randomUUID().toString())
    ).withFallback(ConfigFactory.load("application-test.conf"));
  }

  private static Materializer materializer() {
    return Materializer.matFromSystem(testKit.system().classicSystem());
  }

  @BeforeClass
  public static void before() {
    ClusterSharding clusterSharding = ClusterSharding.get(testKit.system());

    clusterSharding.init(
        Entity.of(
            Device.entityTypeKey,
            entityContext ->
                Device.create(entityContext.getEntityId(), clusterSharding)
        )
    );
    testKit.system().log().info("Test cluster node {}", Cluster.get(testKit.system()).selfMember());

    String host = testKit.system().settings().config().getString("oti.twin.http.server.host");
    int port = testKit.system().settings().config().getInt("oti.twin.http.server.port");
    //httpServer = HttpServer.start(host, port, testKit.system());
  }

  @Test
  public void telemetryRequestToJson() {
    // London across Westminster Bridge at Park Plaza Hotel
    WorldMap.Region region = regionAtLatLng(18, new WorldMap.LatLng(51.50079211, -0.11682093));
    final HttpServer.TelemetryRequest create =
        new HttpServer.TelemetryRequest("create", region.zoom, region.topLeft.lat, region.topLeft.lng, region.botRight.lat, region.botRight.lng);
    testKit.system().log().info("{}", toJson(create));
  }

  @Test
  public void regionToJson() {
    // London across Westminster Bridge at Park Plaza Hotel
    WorldMap.Region region = regionAtLatLng(18, new WorldMap.LatLng(51.50079211, -0.11682093));
    testKit.system().log().info("{}", toJson(region));
  }

  @Test
  public void regionQuerySqlIsInArea() {
    final WorldMap.LatLng topLeft = latLng(85.24439622732126, -168.04687500000003);
    final WorldMap.LatLng botRight = latLng(-85.24439622732126, 167.87109375000003);
    final WorldMap.Region area = new WorldMap.Region(5, topLeft, botRight);

    final String sql = HttpServer.sqlInRegions(area);
    testKit.system().log().info("{}", sql);
    assertNotNull(sql);
  }

  private static HttpEntity.Strict toHttpEntity(Object pojo) {
    return HttpEntities.create(ContentTypes.APPLICATION_JSON, toJson(pojo).getBytes());
  }

  private static String toJson(Object pojo) {
    final ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
    try {
      return ow.writeValueAsString(pojo);
    } catch (JsonProcessingException e) {
      return String.format("{ \"error\" : \"%s\" }", e.getMessage());
    }
  }

  private static String entityAsString(HttpResponse httpResponse, Materializer materializer) {
    return httpResponse.entity().getDataBytes()
        .runReduce(ByteString::concat, materializer)
        .thenApply(ByteString::utf8String)
        .toCompletableFuture().join();
  }
}
