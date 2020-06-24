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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.slf4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static akka.http.javadsl.server.Directives.*;
import static oti.twin.WorldMap.entityIdOf;

public class HttpServer {
  private final ActorSystem<?> actorSystem;
  private final ClusterSharding clusterSharding;
  private final String dbUrl;
  private final String username;
  private final String password;

  static HttpServer start(String host, int port, ActorSystem<?> actorSystem) {
    return new HttpServer(host, port, actorSystem);
  }

  private HttpServer(String host, int port, ActorSystem<?> actorSystem) {
    this.actorSystem = actorSystem;
    clusterSharding = ClusterSharding.get(actorSystem);
    dbUrl = actorSystem.settings().config().getString("oti.twin.sql.url");
    username = actorSystem.settings().config().getString("oti.twin.sql.username");
    password = actorSystem.settings().config().getString("oti.twin.sql.password");

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
        path("telemetry", this::handleTelemetryActionPost),
        path("selection", this::handleSelectionRequest),
        path("selections", this::querySelections)
    );
  }

  private Route handleTelemetryActionPost() {
    return post(
        () -> entity(
            Jackson.unmarshaller(TelemetryRequest.class),
            telemetryRequest -> {
              log().debug("POST {}", telemetryRequest);
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

  private Route handleSelectionRequest() {
    final HttpClient httpClient = new HttpClient(actorSystem);
    return post(
        () -> entity(
            Jackson.unmarshaller(HttpClient.SelectionActionRequest.class),
            selectionActionRequest -> {
              log().debug("POST {}", selectionActionRequest);
              httpClient.post(selectionActionRequest)
                  .thenAccept(selectionActionResponse -> {
                    if (StatusCodes.get(selectionActionResponse.httpStatusCode).isFailure()) {
                      log().warn("POST failed {}", selectionActionResponse);
                    }
                  });
              return complete(StatusCodes.OK, new HttpClient.SelectionActionResponse("Accepted", StatusCodes.OK.intValue(), selectionActionRequest), Jackson.marshaller());
            }
        )
    );
  }

  private Route querySelections() {
    return post(
        () -> entity(
            Jackson.unmarshaller(WorldMap.Region.class),
            queryRegion -> {
              log().debug("POST {}", queryRegion);
              try {
                return complete(StatusCodes.OK, read(queryRegion), Jackson.marshaller());
              } catch (SQLException e) {
                log().warn("Read selections query failed.", e);
                return complete(StatusCodes.INTERNAL_SERVER_ERROR, e.getMessage());
              }
            }
        )
    );
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

    @Override
    public String toString() {
      return String.format("%s[%s, %d, %1.9f, %1.9f, %1.9f, %1.9f]", getClass().getSimpleName(), action, zoom, topLeftLat, topLeftLng, botRightLat, botRightLng);
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

    @Override
    public String toString() {
      return String.format("%s[%d, %s, %s]", getClass().getSimpleName(), httpStatusCode, message, telemetryRequest);
    }
  }

  private String querySelections(WorldMap.Region region) throws SQLException {
    return toJson(read(region));
  }

  private List<DeviceProjector.RegionSummary> read(WorldMap.Region region) throws SQLException {
    try (final Connection connection = DriverManager.getConnection(dbUrl, username, password)) {
      return read(connection, region);
    }
  }

  private List<DeviceProjector.RegionSummary> read(Connection connection, WorldMap.Region region) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      String sql = String.format("select * from region"
              + " where zoom = %d"
              + " and top_left_lat <= %1.9f"
              + " and top_left_lng >= %1.9f"
              + " and bot_right_lat >= %1.9f"
              + " and bot_right_lng <= %1.9f"
              + " and device_count > 0",
          region.zoom, region.topLeft.lat, region.topLeft.lng, region.botRight.lat, region.botRight.lng);
      final ResultSet resultSet = statement.executeQuery(sql);
      List<DeviceProjector.RegionSummary> regionSummaries = new ArrayList<>();
      while (resultSet.next()) {
        regionSummaries.add(new DeviceProjector.RegionSummary(region, resultSet.getInt("device_count"), resultSet.getInt("happy_count"), resultSet.getInt("sad_count")));
      }
      return regionSummaries;
    }
  }

  private static String toJson(Object pojo) {
    final ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
    try {
      return ow.writeValueAsString(pojo);
    } catch (JsonProcessingException e) {
      return String.format("{ \"error\" : \"%s\" }", e.getMessage());
    }
  }

  private Logger log() {
    return actorSystem.log();
  }
}
