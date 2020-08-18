package woe.twin;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.ShardedDaemonProcess;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import akka.projection.ProjectionBehavior;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

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
    //startProjectionSharding(actorSystem);
    startProjectionShardingSingleZoom(actorSystem);
  }

  // Copies the truststore file to the local container file system.
  // Cassandra code does not read from classpath resource.
  private static void awsCassandraTruststoreHack(ActorSystem<?> actorSystem) {
    final String filename = "cassandra-truststore.jks";
    final InputStream inputStream = actorSystem.getClass().getClassLoader().getResourceAsStream(filename);
    final Path target = Paths.get(filename);
    if (inputStream != null) {
      try {
        Files.copy(inputStream, target);
      } catch (IOException e) {
        actorSystem.log().error(String.format("Unable to copy '%s'", filename), e);
      }
    }
  }

  private static void startClusterBootstrap(ActorSystem<?> actorSystem) {
    AkkaManagement.get(actorSystem.classicSystem()).start();
    ClusterBootstrap.get(actorSystem.classicSystem()).start();
  }

  static void startHttpServer(ActorSystem<?> actorSystem) {
    try {
      final String host = InetAddress.getLocalHost().getHostName();
      final int port = actorSystem.settings().config().getInt("woe.twin.http.server.port");
      HttpServer.start(host, port, actorSystem);
    } catch (UnknownHostException e) {
      actorSystem.log().error("Http server start failure.", e);
    }
  }

  static void startGrpcServer(ActorSystem<?> actorSystem) {
    try {
      final String host = InetAddress.getLocalHost().getHostName();
      final int port = actorSystem.settings().config().getInt("woe.twin.grpc.server.port");
      GrpcServer.start(host, port, actorSystem);
    } catch (UnknownHostException e) {
      actorSystem.log().error("gRPC server start failure.", e);
    }
  }

  static void startClusterSharding(ActorSystem<?> actorSystem) {
    ClusterSharding clusterSharding = ClusterSharding.get(actorSystem);
    clusterSharding.init(
        Entity.of(
            Device.entityTypeKey,
            entityContext ->
                Device.create(entityContext.getEntityId(), clusterSharding)
        ).withEntityProps(DispatcherSelector.fromConfig("woe.twin.device-entity-dispatcher"))
    );
  }

  static void startProjectionShardingAllZooms(ActorSystem<?> actorSystem) {
    final DeviceProjectorAllZooms.DbSessionFactory dbSessionFactory = new DeviceProjectorAllZooms.DbSessionFactory(actorSystem);
    final List<String> tags = Device.tagsAll(actorSystem);

    ShardedDaemonProcess.get(actorSystem).init(
        ProjectionBehavior.Command.class,
        "region-summary",
        tags.size(),
        id -> ProjectionBehavior.create(DeviceProjectorAllZooms.start(actorSystem, dbSessionFactory, tags.get(id))),
        ShardedDaemonProcessSettings.create(actorSystem),
        Optional.of(ProjectionBehavior.stopMessage())
    );
  }

  // For every tag there are 16 projection event handlers
  // One event handler per zoom from zoom 3 to 18
  static void startProjectionShardingSingleZoom(ActorSystem<?> actorSystem) {
    final DeviceProjectorSingleZoom.DbSessionFactory dbSessionFactory = new DeviceProjectorSingleZoom.DbSessionFactory(actorSystem);
    final List<String> tags = Device.tagsAll(actorSystem);

    ShardedDaemonProcess.get(actorSystem).init(
        ProjectionBehavior.Command.class,
        "region-summary",
        tags.size() * 16,
        id -> {
          final String tag = tags.get(id / 16);
          final int zoom = 3 + id % 16;
          return ProjectionBehavior.create(DeviceProjectorSingleZoom.start(actorSystem, dbSessionFactory, tag, zoom));
        },
        ShardedDaemonProcessSettings.create(actorSystem),
        Optional.of(ProjectionBehavior.stopMessage())
    );
  }
}
