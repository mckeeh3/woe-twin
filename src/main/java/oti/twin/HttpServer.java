package oti.twin;

import akka.actor.typed.ActorRef;
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
import static oti.twin.WorldMap.entityIdOf;
import static oti.twin.WorldMap.regionForZoom0;

public class HttpServer {
  private final ActorSystem<?> actorSystem;
  private final ClusterSharding clusterSharding;
  private ActorRef<Region.Event> replyToTest; // hack for unit testing

  private HttpServer(String host, int port, ActorSystem<?> actorSystem) {
    this.actorSystem = actorSystem;
    clusterSharding = ClusterSharding.get(actorSystem);
    replyToTest = actorSystem.ignoreRef();

    startHttpServer(host, port);
  }

  static HttpServer start(String host, int port, ActorSystem<?> actorSystem) {
    return new HttpServer(host, port, actorSystem);
  }

  private void startHttpServer(String host, int port) {
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
        path("selection", this::handleSelectionActionPost),
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
                return complete(StatusCodes.OK, TelemetryResponse.ok(telemetryRequest), Jackson.marshaller());
              } catch (IllegalArgumentException e) {
                return complete(StatusCodes.BAD_REQUEST, TelemetryResponse.failed(telemetryRequest), Jackson.marshaller());
              }
            }
        )
    );
  }

  private void submitTelemetryToDevice(TelemetryRequest telemetryRequest) {
    Device.TelemetryCommand telemetryCommand = telemetryRequest.asTelemetryCommand();
    String entityId = entityIdOf(regionForZoom0());
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
    public final TelemetryRequest telemetryRequest;

    @JsonCreator
    public TelemetryResponse(
        @JsonProperty("message") String message,
        @JsonProperty("deviceTelemetryRequest") TelemetryRequest telemetryRequest) {
      this.message = message;
      this.telemetryRequest = telemetryRequest;
    }

    static TelemetryResponse ok(TelemetryRequest telemetryRequest) {
      return new TelemetryResponse("Accepted", telemetryRequest);
    }

    static TelemetryResponse failed(TelemetryRequest telemetryRequest) {
      return new TelemetryResponse("Invalid action", telemetryRequest);
    }
  }

  private Route handleSelectionActionPost() {
    return post(
        () -> entity(
            Jackson.unmarshaller(SelectionActionRequest.class),
            selectionActionRequest -> {
              try {
                submitSelectionToEntity(selectionActionRequest);
                return complete(StatusCodes.OK, SelectionActionResponse.ok(selectionActionRequest), Jackson.marshaller());
              } catch (IllegalArgumentException e) {
                return complete(StatusCodes.BAD_REQUEST, SelectionActionResponse.failed(selectionActionRequest), Jackson.marshaller());
              }
            }
        )
    );
  }

  private void submitSelectionToEntity(SelectionActionRequest selectionActionRequest) {
    Region.SelectionCommand selectionCommand = selectionActionRequest.asSelectionAction(replyToTest);
    String entityId = entityIdOf(regionForZoom0());
    EntityRef<Region.Command> entityRef = clusterSharding.entityRefFor(Region.entityTypeKey, entityId);
    entityRef.tell(selectionCommand);
  }

  public static class SelectionActionRequest {
    public final String action;
    public final int zoom;
    public final double topLeftLat;
    public final double topLeftLng;
    public final double botRightLat;
    public final double botRightLng;

    @JsonCreator
    public SelectionActionRequest(
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

    SelectionActionRequest(String action, WorldMap.Region region) {
      this(action, region.zoom, region.topLeft.lat, region.topLeft.lng, region.botRight.lat, region.botRight.lng);
    }

    Region.SelectionCommand asSelectionAction(ActorRef<Region.Event> replyTo) {
      WorldMap.Region region = new WorldMap.Region(zoom, WorldMap.topLeft(topLeftLat, topLeftLng), WorldMap.botRight(botRightLat, botRightLng));
      switch (action) {
        case "create":
          return new Region.SelectionCreate(region, replyTo);
        case "delete":
          return new Region.SelectionDelete(region, replyTo);
        case "happy":
          return new Region.SelectionHappy(region, replyTo);
        case "sad":
          return new Region.SelectionSad(region, replyTo);
        default:
          throw new IllegalArgumentException(String.format("Action '%s' illegal, must be one of: 'create', 'delete', 'happy', or 'sad'.", action));
      }
    }
  }

  public static class SelectionActionResponse {
    public final String message;
    public final SelectionActionRequest selectionActionRequest;

    @JsonCreator
    public SelectionActionResponse(
        @JsonProperty("message") String message,
        @JsonProperty("selectionActionRequest") SelectionActionRequest selectionActionRequest) {
      this.message = message;
      this.selectionActionRequest = selectionActionRequest;
    }

    static SelectionActionResponse ok(SelectionActionRequest selectionActionRequest) {
      return new SelectionActionResponse("Accepted", selectionActionRequest);
    }

    static SelectionActionResponse failed(SelectionActionRequest selectionActionRequest) {
      return new SelectionActionResponse("Invalid action", selectionActionRequest);
    }

  }

  // Hack for unit testing
  void replyTo(ActorRef<Region.Event> replyTo) {
    this.replyToTest = replyTo;
  }

  private Logger log() {
    return actorSystem.log();
  }
}
