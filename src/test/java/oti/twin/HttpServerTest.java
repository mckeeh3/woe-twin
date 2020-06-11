package oti.twin;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.cluster.Cluster;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import akka.stream.Materializer;
import akka.util.ByteString;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            Region.entityTypeKey,
            entityContext ->
                Region.create(entityContext.getEntityId(), clusterSharding)
        )
    );
    testKit.system().log().info("Test cluster node {}", Cluster.get(testKit.system()).selfMember());

    String host = testKit.system().settings().config().getString("oti.http.host");
    int port = testKit.system().settings().config().getInt("oti.http.port");
    httpServer = HttpServer.start(host, port, testKit.system());
  }

  @Test
  public void submitHttpSelectionZoom16() {
    TestProbe<Region.Event> probe = testKit.createTestProbe();

    // Submit request to create a selected region in London across Westminster Bridge at Park Plaza Hotel
    WorldMap.Region region = WorldMap.regionAtLatLng(16, new WorldMap.LatLng(51.50079211, -0.11682093));
    HttpServer.SelectionActionRequest selectionActionRequest = new HttpServer.SelectionActionRequest("create", region);

    httpServer.replyTo(probe.ref()); // hack to pass probe ref to entity messages

    HttpResponse httpResponse = Http.get(testKit.system().classicSystem())
        .singleRequest(HttpRequest.POST("http://localhost:28080/selection")
            .withEntity(toHttpEntity(selectionActionRequest)))
        .toCompletableFuture().join();

    probe.receiveSeveralMessages(1, Duration.ofSeconds(15));
    assertTrue(httpResponse.status().isSuccess());

    String response = entityAsString(httpResponse, materializer());
    assertNotNull(response);
    assertTrue(response.contains("\"message\":\"Accepted\""));
  }

  @Ignore
  @Test // Not a test. Shows Json of a selection request expected in HTTP post.
  public void toJsonSelectionActionRequest() {
    // Submit request to create a selected region in London across Westminster Bridge at Park Plaza Hotel
    WorldMap.Region region = WorldMap.regionAtLatLng(16, new WorldMap.LatLng(51.50079211, -0.11682093));
    HttpServer.SelectionActionRequest selectionActionRequest = new HttpServer.SelectionActionRequest("create", region);

    System.out.println(toJson(selectionActionRequest));

    // { "action" : "create", "zoom" : 16, "topLeftLat" : 51.50146484375, "topLeftLng" : -0.1171875, "botRightLat" : 51.4990234375, "botRightLng" : -0.11474609375 }
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