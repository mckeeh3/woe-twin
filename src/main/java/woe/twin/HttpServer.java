package woe.twin;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.completeOKWithFuture;
import static akka.http.javadsl.server.Directives.concat;
import static akka.http.javadsl.server.Directives.entity;
import static akka.http.javadsl.server.Directives.getFromResource;
import static akka.http.javadsl.server.Directives.onSuccess;
import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.post;
import static woe.twin.WorldMap.entityIdOf;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.DispatcherSelector;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.http.javadsl.Http;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;

public class HttpServer {
  private final ActorSystem<?> actorSystem;
  private final ClusterSharding clusterSharding;
  private final DataSource dataSource;
  private final String homepageFilename;

  static void start(String host, int port, ActorSystem<?> actorSystem) {
    new HttpServer(host, port, actorSystem);
  }

  private HttpServer(String host, int port, ActorSystem<?> actorSystem) {
    this.actorSystem = actorSystem;
    clusterSharding = ClusterSharding.get(actorSystem);
    dataSource = dataSource(actorSystem);
    homepageFilename = actorSystem.settings().config().getString("woe.twin.homepage-filename");

    start(host, port);
  }

  private void start(String host, int port) {
    Http.get(actorSystem).newServerAt(host, port).bind(route());
    log().info("HTTP Server started on {}:{}", host, port);
  }

  private Route route() {
    return concat(
        path("", homePage()),
        path("woe.html", homePage()),
        path("woe.js", () -> getFromResource("woe.js", ContentTypes.APPLICATION_JSON)),
        path("p5.js", () -> getFromResource("p5.js", ContentTypes.APPLICATION_JSON)),
        path("mappa.js", () -> getFromResource("mappa.js", ContentTypes.APPLICATION_JSON)),
        path("favicon.ico", () -> getFromResource("favicon.ico", MediaTypes.IMAGE_X_ICON.toContentType())),
        path("telemetry", this::handleTelemetryActionPost),
        path("selection", this::handleSelectionRequest),
        path("query-devices", this::queryDevices)
    );
  }

  private Supplier<Route> homePage() {
    return () -> getFromResource(homepageFilename, ContentTypes.TEXT_HTML_UTF8);
  }

  private Route handleTelemetryActionPost() {
    return post(
        () -> entity(
            Jackson.unmarshaller(Telemetry.TelemetryRequest.class),
            telemetryRequest -> {
              if (!telemetryRequest.action.equals("ping")) {
                log().debug("POST {}", telemetryRequest);
              }
              return onSuccess(submitTelemetryToDevice(telemetryRequest),
                  telemetryResponse -> complete(StatusCodes.get(telemetryResponse.httpStatusCode), telemetryResponse, Jackson.marshaller()));
            }
        )
    );
  }

  private CompletionStage<Telemetry.TelemetryResponse> submitTelemetryToDevice(Telemetry.TelemetryRequest telemetryRequest) {
    final var entityId = entityIdOf(telemetryRequest.region);
    final var entityRef = clusterSharding.entityRefFor(Device.entityTypeKey, entityId);
    return entityRef.ask(telemetryRequest::asTelemetryCommand, Duration.ofSeconds(30))
        .handle((reply, e) -> {
          if (reply != null) {
            return Telemetry.TelemetryResponse.ok(StatusCodes.OK.intValue(), telemetryRequest);
          } else {
            return Telemetry.TelemetryResponse.failed(e.getMessage(), StatusCodes.INTERNAL_SERVER_ERROR.intValue(), telemetryRequest);
          }
        });
  }

  private Route handleSelectionRequest() {
    final var httpClient = new HttpClient(actorSystem);
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

  private Route queryDevices() {
    return post(
        () -> entity(
            Jackson.unmarshaller(WorldMap.Region.class),
            queryRegion -> completeOKWithFuture(
                CompletableFuture.supplyAsync(() -> {
                  try {
                    return query(queryRegion);
                  } catch (SQLException e) {
                    log().warn("Read selections query failed.", e);
                    return new QueryResponse(0, 0, 0);
                  }
                }, actorSystem.dispatchers().lookup(DispatcherSelector.fromConfig("woe.twin.query-devices-dispatcher"))),
                Jackson.marshaller()
            )
        )
    );
  }

  private static DataSource dataSource(ActorSystem<?> actorSystem) {
    final var akkaConfig = actorSystem.settings().config();
    final var dbUrl = akkaConfig.getString("woe.twin.sql.url");
    final var username = akkaConfig.getString("woe.twin.sql.username");
    final var password = akkaConfig.getString("woe.twin.sql.password");
    final var maxPoolSize = akkaConfig.getInt("woe.twin.sql.max-pool-size");

    final HikariConfig config = new HikariConfig();
    config.setJdbcUrl(dbUrl);
    config.setUsername(username);
    config.setPassword(password);
    config.setMaximumPoolSize(maxPoolSize);
    config.setAutoCommit(false);

    return new HikariDataSource(config);
  }

  private QueryResponse query(WorldMap.Region region) throws SQLException {
    final var queryResponse = queryDeviceTotals();
    try (final Connection connection = dataSource.getConnection()) {
      queryResponse.regionSummaries = query(connection, region);
      return queryResponse;
    }
  }

  private QueryResponse queryDeviceTotals() throws SQLException {
    final var sql = "select sum(device_count), sum(happy_count), sum(sad_count) from woe_twin_region where zoom = 3";
    try (final Connection connection = dataSource.getConnection();
          final Statement statement = connection.createStatement()) {
      final var resultSet = statement.executeQuery(sql);
      if (resultSet.next()) {
        return new QueryResponse(resultSet.getInt(1), resultSet.getInt(2), resultSet.getInt(3));
      } else {
        return new QueryResponse(0, 0, 0);
      }
    }
  }

  private List<DeviceProjectorSingleZoom.RegionSummary> query(Connection connection, WorldMap.Region regionQuery) throws SQLException {
    //final long start = System.nanoTime();
    final var regionSummaries = new ArrayList<DeviceProjectorSingleZoom.RegionSummary>();
    try (final Statement statement = connection.createStatement()) {
      final ResultSet resultSet = statement.executeQuery(sqlInRange(regionQuery));
      while (resultSet.next()) {
        final WorldMap.LatLng topLeft = new WorldMap.LatLng(resultSet.getFloat("top_left_lat"), resultSet.getFloat("top_left_lng"));
        final WorldMap.LatLng botRight = new WorldMap.LatLng(resultSet.getFloat("bot_right_lat"), resultSet.getFloat("bot_right_lng"));
        final WorldMap.Region region = new WorldMap.Region(resultSet.getInt("zoom"), topLeft, botRight);
        regionSummaries
            .add(new DeviceProjectorSingleZoom.RegionSummary(region, resultSet.getInt("device_count"), resultSet.getInt("happy_count"), resultSet.getInt("sad_count")));
      }
      //log().debug("UI query {}, zoom {}, regions {}", String.format("%,dns", System.nanoTime() - start), regionQuery.zoom, regionSummaries.size());
      return regionSummaries;
    }
  }

  private static String sqlInRange(WorldMap.Region regionQuery) {
    return String.format("select * from woe_twin_region"
            + " where zoom = %d"
            + " and top_left_lat <= %1.9f"
            + " and top_left_lng >= %1.9f"
            + " and bot_right_lat >= %1.9f"
            + " and bot_right_lng <= %1.9f"
            + " and device_count > 0",
        regionQuery.zoom, regionQuery.topLeft.lat, regionQuery.topLeft.lng, regionQuery.botRight.lat, regionQuery.botRight.lng);
  }

  // This was an experiment suggested by Yugabyte as an attempt to do selects when using HASH on the primary key
  // The query response times were too high
  static String sqlInRegions(WorldMap.Region regionQuery) {
    final var sql = new StringBuilder();
    final var regions = WorldMap.regionsIn(regionQuery);
    final var nl = String.format("%n");
    var delimiter = "";

    sql.append("select * from woe_twin_region").append(nl);
    sql.append(" where (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)").append(nl);
    sql.append(" in (values ");

    for (WorldMap.Region r : regions) {
      sql.append(String.format("%s%n", delimiter));
      sql.append(String.format("(%d, %1.9f, %1.9f, %1.9f, %1.9f)", r.zoom, r.topLeft.lat, r.topLeft.lng, r.botRight.lat, r.botRight.lng));
      delimiter = ",";
    }

    sql.append(")").append(nl);

    return sql.toString();
  }

  public static class QueryResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    public final int deviceCount;
    public final int happyCount;
    public final int sadCount;
    public List<DeviceProjectorSingleZoom.RegionSummary> regionSummaries;

    public QueryResponse(int deviceCount, int happyCount, int sadCount) {
      this.deviceCount = deviceCount;
      this.happyCount = happyCount;
      this.sadCount = sadCount;
      regionSummaries = new ArrayList<>();
    }
  }

  private Logger log() {
    return actorSystem.log();
  }
}
