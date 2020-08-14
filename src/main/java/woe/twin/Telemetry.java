package woe.twin;

import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public interface Telemetry {
  class TelemetryRequest {
    public final String action;
    public final int zoom;
    public final double topLeftLat;
    public final double topLeftLng;
    public final double botRightLat;
    public final double botRightLng;
    public final WorldMap.Region region;

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
      region = new WorldMap.Region(zoom, WorldMap.topLeft(topLeftLat, topLeftLng), WorldMap.botRight(botRightLat, botRightLng));
    }

    Device.TelemetryCommand asTelemetryCommand(ActorRef<Device.TelemetryResponse> replyTo) {
      switch (action) {
        case "create":
          return new Device.TelemetryCreateCommand(region, replyTo);
        case "delete":
          return new Device.TelemetryDeleteCommand(region, replyTo);
        case "happy":
          return new Device.TelemetryHappyCommand(region, replyTo);
        case "sad":
          return new Device.TelemetrySadCommand(region, replyTo);
        case "ping":
          return new Device.TelemetryPingCommand(region, replyTo);
        default:
          throw new IllegalArgumentException(String.format("Action '%s' illegal, must be one of: 'create', 'delete', 'happy', or 'sad'.", action));
      }
    }

    @Override
    public String toString() {
      return String.format("%s[%s, %d, %1.9f, %1.9f, %1.9f, %1.9f]", getClass().getSimpleName(), action, zoom, topLeftLat, topLeftLng, botRightLat, botRightLng);
    }
  }

  class TelemetryResponse {
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
}
