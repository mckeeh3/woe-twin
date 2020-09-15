package woe.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static woe.twin.WorldMap.latLng;
import static woe.twin.WorldMap.regionAtLatLng;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.cluster.Cluster;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;

public class HttpServerTest {
  private static String selectionUrl;

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource(config());

  private static Config config() {
    return ConfigFactory.parseString(
        String.format("akka.cluster.seed-nodes = [ \"akka://%s@127.0.0.1:25520\" ] %n", HttpServerTest.class.getSimpleName())
            + String.format("akka.persistence.snapshot-store.local.dir = \"%s-%s\" %n", "target/snapshot", UUID.randomUUID().toString())
    ).withFallback(ConfigFactory.load("application-test.conf"));
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

    String host = testKit.system().settings().config().getString("woe.twin.http.server.host");
    int port = testKit.system().settings().config().getInt("woe.twin.http.server.port");
    //HttpServer.start(host, port, testKit.system());
    selectionUrl = String.format("http://%s:%d/selection", host, port);
  }

  @Test
  public void telemetryRequestToJson() {
    // London across Westminster Bridge at Park Plaza Hotel
    WorldMap.Region region = regionAtLatLng(18, new WorldMap.LatLng(51.50079211, -0.11682093));
    final Telemetry.TelemetryRequest create =
        new Telemetry.TelemetryRequest("create", region.zoom, region.topLeft.lat, region.topLeft.lng, region.botRight.lat, region.botRight.lng);
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

  @Ignore
  @Test
  public void selectionActionRequestPostWorks() {
    WorldMap.Region region = WorldMap.regionAtLatLng(16, new WorldMap.LatLng(51.50079211, -0.11682093));
    HttpClient.SelectionActionRequest selectionActionRequest =
        new HttpClient.SelectionActionRequest("create", 100, region.zoom, region.topLeft.lat, region.topLeft.lng, region.botRight.lat, region.botRight.lng);

    HttpResponse httpResponse = Http.get(testKit.system().classicSystem())
        .singleRequest(HttpRequest.POST(selectionUrl)
            .withEntity(toHttpEntity(selectionActionRequest)))
        .toCompletableFuture().join();

    assertEquals(200, httpResponse.status().intValue());
  }
/*
  private static class MaybeRespond {
    static Behavior<Object> create() {
      return Behaviors.setup(ctx -> new MaybeRespond().behavior());
    }

    private Behavior<Object> behavior() {
      return Behaviors.receive(Object.class)
          .onMessage(ActorRef.class, this::onActorRef)
          .onAnyMessage(this::onMessage)
          .build();
    }

    private Behavior<Object> onActorRef(ActorRef<Object> actorRef) {
      actorRef.tell("Success!");
      return Behaviors.same();
    }

    private Behavior<Object> onMessage(Object message) {
      testKit.system().log().info("Message '{}', not sending response message.", message);
      return Behaviors.same();
    }
  }
*/
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
/*
  private static String entityAsString(HttpResponse httpResponse, Materializer materializer) {
    return httpResponse.entity().getDataBytes()
        .runReduce(ByteString::concat, materializer)
        .thenApply(ByteString::utf8String)
        .toCompletableFuture().join();
  }
*/
}
