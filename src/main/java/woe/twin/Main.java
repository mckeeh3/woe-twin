package woe.twin;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;

public class Main {
  static Behavior<Void> create() {
    return Behaviors.setup(
        context -> Behaviors.receive(Void.class)
            .onSignal(Terminated.class, signal -> Behaviors.stopped())
            .build()
    );
  }

  public static void main(String[] args) {
    ActorSystem<?> actorSystem = ActorSystem.create(Main.create(), "woe-twin");
    awsCassandraTruststoreHack(actorSystem);
    startClusterBootstrap(actorSystem);
    startHttpServer(actorSystem);
    startGrpcServer(actorSystem);
    startClusterSharding(actorSystem);
    startProjection(actorSystem);
  }

  // Copies the truststore file to the local container file system.
  // Cassandra code does not read from classpath resource.
  private static void awsCassandraTruststoreHack(ActorSystem<?> actorSystem) {
    final var filename = "cassandra-truststore.jks";
    final var inputStream = actorSystem.getClass().getClassLoader().getResourceAsStream(filename);
    final var target = Paths.get(filename);
    if (inputStream != null) {
      try {
        Files.copy(inputStream, target);
      } catch (IOException e) {
        actorSystem.log().error(String.format("Unable to copy '%s'", filename), e);
      }
    }
  }

  private static void startClusterBootstrap(ActorSystem<?> actorSystem) {
    AkkaManagement.get(actorSystem).start();
    ClusterBootstrap.get(actorSystem).start();
  }

  static void startHttpServer(ActorSystem<?> actorSystem) {
    try {
      final var host = InetAddress.getLocalHost().getHostName();
      final var port = actorSystem.settings().config().getInt("woe.twin.http.server.port");
      HttpServer.start(host, port, actorSystem);
    } catch (UnknownHostException e) {
      actorSystem.log().error("Http server start failure.", e);
    }
  }

  static void startGrpcServer(ActorSystem<?> actorSystem) {
    try {
      final var host = InetAddress.getLocalHost().getHostName();
      final var port = actorSystem.settings().config().getInt("woe.twin.grpc.server.port");
      GrpcServer.start(host, port, actorSystem);
    } catch (UnknownHostException e) {
      actorSystem.log().error("gRPC server start failure.", e);
    }
  }

  static void startClusterSharding(ActorSystem<?> actorSystem) {
    final var clusterSharding = ClusterSharding.get(actorSystem);
    clusterSharding.init(
        Entity.of(
            Device.entityTypeKey,
            entityContext ->
                Device.create(entityContext.getEntityId(), clusterSharding)
        )
        .withEntityProps(DispatcherSelector.fromConfig("woe.twin.device-entity-dispatcher"))
        .withStopMessage(Device.Passivate.INSTANCE)
    );
  }

  static void startProjection(ActorSystem<?> actorSystem) {
    //DeviceProjectorAllZooms.start(actorSystem);
    //DeviceProjectorSingleZoom.start(actorSystem);
    DeviceProjectionFiltered.start(actorSystem);
  }
}
