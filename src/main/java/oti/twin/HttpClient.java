package oti.twin;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpRequest;
import akka.stream.Materializer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

class HttpClient {
  private final ActorSystem<?> actorSystem;
  private final Materializer materializer;
  private final String url;

  HttpClient(ActorSystem<?> actorSystem) {
    this(actorSystem, url(actorSystem));
  }

  HttpClient(ActorSystem<?> actorSystem, String url) {
    this.actorSystem = actorSystem;
    this.materializer = Materializer.matFromSystem(actorSystem.classicSystem());
    this.url = url;
  }

  private static String url(ActorSystem<?> actorSystem) {
    final String host = actorSystem.settings().config().getString("oti_twin_http_server_host");
    final int port = actorSystem.settings().config().getInt("oti_twin_http_server_port");
    return String.format("http://%s:%d/telemetry", host, port);
  }

  private CompletionStage<SelectionActionResponse> post(SelectionActionRequest selectionActionRequest) {
    return Http.get(actorSystem.classicSystem())
        .singleRequest(HttpRequest.POST(url)
            .withEntity(toHttpEntity(selectionActionRequest)))
        .thenCompose(r -> {
          if (r.status().isSuccess()) {
            return Jackson.unmarshaller(SelectionActionResponse.class).unmarshal(r.entity(), materializer);
          } else {
            return CompletableFuture.completedFuture(new SelectionActionResponse(r.status().reason(), r.status().intValue(), selectionActionRequest));
          }
        });
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
  }

  public static class SelectionActionResponse {
    public final String message;
    public final int httpStatusCode;
    public final SelectionActionRequest selectionActionRequest;

    @JsonCreator
    public SelectionActionResponse(
        @JsonProperty("message") String message,
        @JsonProperty("httpStatusCode") int httpStatusCode,
        @JsonProperty("selectionActionRequest") SelectionActionRequest selectionActionRequest) {
      this.message = message;
      this.httpStatusCode = httpStatusCode;
      this.selectionActionRequest = selectionActionRequest;
    }

    static SelectionActionResponse ok(SelectionActionRequest selectionActionRequest) {
      return new SelectionActionResponse("Accepted", -1, selectionActionRequest);
    }

    static SelectionActionResponse failed(SelectionActionRequest selectionActionRequest) {
      return new SelectionActionResponse("Invalid action", -1, selectionActionRequest);
    }
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
}
