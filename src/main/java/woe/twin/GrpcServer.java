package woe.twin;

import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.StatusCodes;
import akka.stream.Materializer;
import woe.twin.grpc.TelemetryRequestGrpc;
import woe.twin.grpc.TelemetryResponseGrpc;
import woe.twin.grpc.TwinDeviceService;
import woe.twin.grpc.TwinDeviceServiceHandlerFactory;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import static woe.twin.WorldMap.entityIdOf;

public class GrpcServer {
  private final ActorSystem<?> actorSystem;
  private final ClusterSharding clusterSharding;

  static void start(String host, int port, ActorSystem<?> actorSystem) {
    new GrpcServer(host, port, actorSystem);
  }
  private GrpcServer(String host, int port, ActorSystem<?> actorSystem) {
    this.actorSystem = actorSystem;
    clusterSharding = ClusterSharding.get(actorSystem);

    start(host, port).thenAccept(binding -> {
      actorSystem.log().info("gRPC server started on {}", binding.localAddress());
    });
  }

  CompletionStage<ServerBinding> start(String host, int port) {
    final TelemetryServiceImpl telemetryServiceImpl = new TelemetryServiceImpl(clusterSharding);
//    Http.get(actorSystem).newServerAt(host, port).bind(TwinDeviceServiceHandlerFactory.create(telemetryServiceImpl, actorSystem));

    return Http.get(actorSystem.classicSystem()).bindAndHandleAsync(
        TwinDeviceServiceHandlerFactory.create(telemetryServiceImpl, actorSystem),
        ConnectHttp.toHost(host, port),
        Materializer.matFromSystem(actorSystem)
    );
  }

  private static Telemetry.TelemetryRequest toTelemetryRequest(TelemetryRequestGrpc telemetryRequest) {
    return new Telemetry.TelemetryRequest(telemetryRequest.getAction(), telemetryRequest.getZoom(), telemetryRequest.getTopLeftLat(),
        telemetryRequest.getTopLeftLng(), telemetryRequest.getBotRightLat(), telemetryRequest.getBotRightLng());
  }

  private static TelemetryResponseGrpc toTelemetryResponseGrpc(Telemetry.TelemetryResponse telemetryResponse) {
    return TelemetryResponseGrpc.newBuilder()
        .setMessage(telemetryResponse.message)
        .setHttpStatusCode(telemetryResponse.httpStatusCode)
        .setTelemetryRequest(toTelemetryRequestGrpc(telemetryResponse.telemetryRequest))
        .build();
  }

  private static TelemetryRequestGrpc toTelemetryRequestGrpc(Telemetry.TelemetryRequest telemetryRequest) {
    return TelemetryRequestGrpc.newBuilder()
        .setAction(telemetryRequest.action)
        .setZoom(telemetryRequest.zoom)
        .setTopLeftLat(telemetryRequest.topLeftLat)
        .setTopLeftLng(telemetryRequest.topLeftLng)
        .setBotRightLat(telemetryRequest.botRightLat)
        .setBotRightLng(telemetryRequest.botRightLng)
        .build();
  }

  static class TelemetryServiceImpl implements TwinDeviceService {
    private final ClusterSharding clusterSharding;

    TelemetryServiceImpl(ClusterSharding clusterSharding) {
      this.clusterSharding = clusterSharding;
    }

    @Override
    public CompletionStage<TelemetryResponseGrpc> telemetry(TelemetryRequestGrpc telemetryRequest) {
      return submitTelemetryToDevice(toTelemetryRequest(telemetryRequest))
          .thenApply(GrpcServer::toTelemetryResponseGrpc);
    }

    private CompletionStage<Telemetry.TelemetryResponse> submitTelemetryToDevice(Telemetry.TelemetryRequest telemetryRequest) {
      String entityId = entityIdOf(telemetryRequest.region);
      EntityRef<Device.Command> entityRef = clusterSharding.entityRefFor(Device.entityTypeKey, entityId);
      return entityRef.ask(telemetryRequest::asTelemetryCommand, Duration.ofSeconds(30))
          .handle((reply, e) -> {
            if (reply != null) {
              return Telemetry.TelemetryResponse.ok(StatusCodes.OK.intValue(), telemetryRequest);
            } else {
              return Telemetry.TelemetryResponse.failed(e.getMessage(), StatusCodes.INTERNAL_SERVER_ERROR.intValue(), telemetryRequest);
            }
          });
    }
  }
}
