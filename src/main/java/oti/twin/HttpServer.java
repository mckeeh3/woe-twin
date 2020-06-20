package oti.twin;

import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.stream.Materializer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;

import static akka.http.javadsl.server.Directives.*;
import static oti.twin.WorldMap.*;

public class HttpServer {
  private final ActorSystem<?> actorSystem;
  private final ClusterSharding clusterSharding;

  static HttpServer start(String host, int port, ActorSystem<?> actorSystem) {
    return new HttpServer(host, port, actorSystem);
  }

  private HttpServer(String host, int port, ActorSystem<?> actorSystem) {
    this.actorSystem = actorSystem;
    clusterSharding = ClusterSharding.get(actorSystem);

    start(host, port);
  }

  private void start(String host, int port) {
    Materializer materializer = Materializer.matFromSystem(actorSystem);

    Http.get(actorSystem.classicSystem())
        .bindAndHandle(route().flow(actorSystem.classicSystem(), materializer),
            ConnectHttp.toHost(host, port), materializer);

    log().info("HTTP Server started on {}:{}", host, "" + port);
  }

  private Route route() {
    return concat(
        path("", () -> getFromResource("oti.html", ContentTypes.TEXT_HTML_UTF8)),
        path("oti.html", () -> getFromResource("oti.html", ContentTypes.TEXT_HTML_UTF8)),
        path("oti.js", () -> getFromResource("oti.js", ContentTypes.APPLICATION_JSON)),
        path("p5.js", () -> getFromResource("p5.js", ContentTypes.APPLICATION_JSON)),
        path("mappa.js", () -> getFromResource("mappa.js", ContentTypes.APPLICATION_JSON)),
        path("telemetry", this::handleTelemetryActionPost)
    );
  }

  private Route handleTelemetryActionPost() {
    return post(
        () -> entity(
            Jackson.unmarshaller(TelemetryRequest.class),
            telemetryRequest -> {
              try {
                submitTelemetryToDevice(telemetryRequest);
                return complete(StatusCodes.OK, TelemetryResponse.ok(StatusCodes.OK.intValue(), telemetryRequest), Jackson.marshaller());
              } catch (IllegalArgumentException e) {
                return complete(StatusCodes.BAD_REQUEST, TelemetryResponse.failed(e.getMessage(), StatusCodes.BAD_REQUEST.intValue(), telemetryRequest), Jackson.marshaller());
              }
            }
        )
    );
  }

  private void submitTelemetryToDevice(TelemetryRequest telemetryRequest) {
    Device.TelemetryCommand telemetryCommand = telemetryRequest.asTelemetryCommand();
    String entityId = entityIdOf(telemetryCommand.region);
    EntityRef<Device.Command> entityRef = clusterSharding.entityRefFor(Device.entityTypeKey, entityId);
    entityRef.tell(telemetryCommand);
  }

  public static class TelemetryRequest {
    public final String action;
    public final int zoom;
    public final double topLeftLat;
    public final double topLeftLng;
    public final double botRightLat;
    public final double botRightLng;

    @JsonCreator
    public TelemetryRequest(
        @JsonProperty("action") String action,
        @JsonProperty("zoom") int zoom,
        @JsonProperty("topLeftLat") double topLeftLat,
        @JsonProperty("topLeftLng") double topLeftLng,
        @JsonProperty("botRightLat") double botRightLat,
        @JsonProperty("botRightLng") double botRightLng) {
      this.action = action;
      this.zoom = zoom;
      this.topLeftLat = topLeftLat;
      this.topLeftLng = topLeftLng;
      this.botRightLat = botRightLat;
      this.botRightLng = botRightLng;
    }

    Device.TelemetryCommand asTelemetryCommand() {
      WorldMap.Region region = new WorldMap.Region(zoom, WorldMap.topLeft(topLeftLat, topLeftLng), WorldMap.botRight(botRightLat, botRightLng));
      switch (action) {
        case "create":
          return new Device.TelemetryCreateCommand(region);
        case "delete":
          return new Device.TelemetryDeleteCommand(region);
        case "happy":
          return new Device.TelemetryHappyCommand(region);
        case "sad":
          return new Device.TelemetrySadCommand(region);
        default:
          throw new IllegalArgumentException(String.format("Action '%s' illegal, must be one of: 'create', 'delete', 'happy', or 'sad'.", action));
      }
    }
  }

  public static class TelemetryResponse {
    public final String message;
    public final int httpStatusCode;
    public final TelemetryRequest telemetryRequest;

    @JsonCreator
    public TelemetryResponse(
        @JsonProperty("message") String message,
        @JsonProperty("httpStatusCode") int httpStatusCode,
        @JsonProperty("deviceTelemetryRequest") TelemetryRequest telemetryRequest) {
      this.message = message;
      this.httpStatusCode = httpStatusCode;
      this.telemetryRequest = telemetryRequest;
    }

    static TelemetryResponse ok(int httpStatusCode, TelemetryRequest telemetryRequest) {
      return new TelemetryResponse("Accepted", httpStatusCode, telemetryRequest);
    }

    static TelemetryResponse failed(String message, int httpStatusCode, TelemetryRequest telemetryRequest) {
      return new TelemetryResponse(message, httpStatusCode, telemetryRequest);
    }
  }

  private Logger log() {
    return actorSystem.log();
  }
}
