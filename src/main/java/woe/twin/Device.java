package woe.twin;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.IntStream;

import static woe.twin.WorldMap.*;

class Device extends EventSourcedBehavior<Device.Command, Device.Event, Device.State> {
  final String entityId;
  final WorldMap.Region region;
  final Set<String> tags;
  final ClusterSharding clusterSharding;
  final ActorContext<Device.Command> actorContext;
  static final EntityTypeKey<Device.Command> entityTypeKey = EntityTypeKey.create(Device.Command.class, Device.class.getSimpleName());

  static Behavior<Device.Command> create(String entityId, ClusterSharding clusterSharding) {
    return Behaviors.setup(actorContext -> new Device(entityId, clusterSharding, actorContext));
  }

  private Device(String entityId, ClusterSharding clusterSharding, ActorContext<Device.Command> actorContext) {
    super(PersistenceId.of(entityTypeKey.name(), entityId));
    this.entityId = entityId;
    this.region = WorldMap.regionForEntityId(entityId);
    this.clusterSharding = clusterSharding;
    this.actorContext = actorContext;
    tags = tagsForEntity();
  }

  @Override
  public State emptyState() {
    return new State(region);
  }

  @Override
  public CommandHandler<Command, Event, State> commandHandler() {
    return newCommandHandlerBuilder().forAnyState()
        .onCommand(TelemetryCreateCommand.class, this::onCreateCommand)
        .onCommand(TelemetryDeleteCommand.class, this::onDeleteCommand)
        .onCommand(TelemetryHappyCommand.class, this::onHappyCommand)
        .onCommand(TelemetrySadCommand.class, this::onSadCommand)
        .onCommand(TelemetryPingCommand.class, this::onPingCommand)
        .build();
  }

  private Effect<Event, State> onCreateCommand(State state, TelemetryCreateCommand telemetryCreateCommand) {
    if (state.isInactive()) {
      log().info("{}", telemetryCreateCommand);
      return Effect().persist(new DeviceActivated(telemetryCreateCommand.region));
    }
    return Effect().none();
  }

  private Effect<Event, State> onDeleteCommand(State state, TelemetryDeleteCommand telemetryDeleteCommand) {
    if (state.isActive()) {
      if (state.isHappy()) {
        return Effect().persist(new DeviceDeactivatedHappy(telemetryDeleteCommand.region));
      } else {
        return Effect().persist(new DeviceDeactivatedSad(telemetryDeleteCommand.region));
      }
    }
    return Effect().none();
  }

  private Effect<Event, State> onHappyCommand(State state, TelemetryHappyCommand telemetryHappyCommand) {
    if (state.isActive() && state.isSad()) {
      return Effect().persist(new DeviceMadeHappy(telemetryHappyCommand.region));
    }
    return Effect().none();
  }

  private Effect<Event, State> onSadCommand(State state, TelemetrySadCommand telemetrySadCommand) {
    if (state.isActive() && state.isHappy()) {
      return Effect().persist(new DeviceMadeSad(telemetrySadCommand.region));
    }
    return Effect().none();
  }

  private Effect<Event, State> onPingCommand(State state, TelemetryPingCommand telemetryPingCommand) {
    if (state.isInactive()) {
      log().info("Ping create inactive device {}", telemetryPingCommand);
      return Effect().persist(new DeviceActivated(telemetryPingCommand.region));
    }
    return Effect().none();
  }

  @Override
  public EventHandler<State, Event> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(DeviceActivated.class, State::deviceActivated)
        .onEvent(DeviceDeactivatedHappy.class, State::deviceDeactivated)
        .onEvent(DeviceDeactivatedSad.class, State::deviceDeactivated)
        .onEvent(DeviceMadeHappy.class, State::deviceMadeHappy)
        .onEvent(DeviceMadeSad.class, State::deviceMadeSad)
        .onEvent(DevicePinged.class, State::devicePinged)
        .build();
  }

  @Override
  public Set<String> tagsFor(Event event) {
    return tags;
  }

  interface Command extends CborSerializable {
  }

  public abstract static class TelemetryCommand implements Command {
    public final WorldMap.Region region;

    public TelemetryCommand(WorldMap.Region region) {
      this.region = region;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TelemetryCommand that = (TelemetryCommand) o;
      return region.equals(that.region);
    }

    @Override
    public int hashCode() {
      return Objects.hash(region);
    }

    @Override
    public String toString() {
      return String.format("%s[%s]", getClass().getSimpleName(), region);
    }
  }

  public static class TelemetryCreateCommand extends TelemetryCommand {
    @JsonCreator
    public TelemetryCreateCommand(@JsonProperty("region") WorldMap.Region region) {
      super(region);
    }
  }

  public static class TelemetryDeleteCommand extends TelemetryCommand {
    @JsonCreator
    public TelemetryDeleteCommand(@JsonProperty("region") WorldMap.Region region) {
      super(region);
    }
  }

  public static class TelemetryHappyCommand extends TelemetryCommand {
    @JsonCreator
    public TelemetryHappyCommand(@JsonProperty("region") WorldMap.Region region) {
      super(region);
    }
  }

  public static class TelemetrySadCommand extends TelemetryCommand {
    @JsonCreator
    public TelemetrySadCommand(@JsonProperty("region") WorldMap.Region region) {
      super(region);
    }
  }

  public static class TelemetryPingCommand extends TelemetryCommand {
    @JsonCreator
    public TelemetryPingCommand(@JsonProperty("region") WorldMap.Region region) {
      super(region);
    }
  }

  interface Event extends CborSerializable {
  }

  public abstract static class DeviceEvent implements Event {
    final WorldMap.Region region;

    public DeviceEvent(WorldMap.Region region) {
      this.region = region;
    }

    @Override
    public String toString() {
      return String.format("%s[%s]", getClass().getSimpleName(), region);
    }
  }

  public static class DeviceActivated extends DeviceEvent {
    @JsonCreator
    public DeviceActivated(WorldMap.Region region) {
      super(region);
    }
  }

  public static class DeviceDeactivatedHappy extends DeviceEvent {
    @JsonCreator
    public DeviceDeactivatedHappy(WorldMap.Region region) {
      super(region);
    }
  }

  public static class DeviceDeactivatedSad extends DeviceEvent {
    @JsonCreator
    public DeviceDeactivatedSad(WorldMap.Region region) {
      super(region);
    }
  }

  public static class DeviceMadeHappy extends DeviceEvent {
    @JsonCreator
    public DeviceMadeHappy(WorldMap.Region region) {
      super(region);
    }
  }

  public static class DeviceMadeSad extends DeviceEvent {
    @JsonCreator
    public DeviceMadeSad(WorldMap.Region region) {
      super(region);
    }
  }

  public static class DevicePinged extends DeviceEvent {
    @JsonCreator
    public DevicePinged(WorldMap.Region region) {
      super(region);
    }
  }

  static final class State implements CborSerializable {
    final WorldMap.Region region;
    boolean active;
    boolean happy;

    State(WorldMap.Region region) {
      this.region = region;
    }

    boolean isActive() {
      return active;
    }

    boolean isInactive() {
      return !active;
    }

    void activate() {
      active = true;
    }

    void deactivate() {
      active = false;
    }

    boolean isHappy() {
      return active && happy;
    }

    boolean isSad() {
      return active && !happy;
    }

    void makeHappy() {
      happy = true;
    }

    void makeSad() {
      happy = false;
    }

    public State deviceActivated(DeviceActivated deviceActivated) {
      activate();
      makeHappy();
      return this;
    }

    public State deviceDeactivated(DeviceDeactivatedHappy deviceDeactivated) {
      deactivate();
      return this;
    }

    public State deviceDeactivated(DeviceDeactivatedSad deviceDeactivated) {
      deactivate();
      return this;
    }

    public State deviceMadeHappy(DeviceMadeHappy deviceMadeHappy) {
      makeHappy();
      return this;
    }

    public State deviceMadeSad(DeviceMadeSad deviceMadeSad) {
      makeSad();
      return this;
    }

    public State devicePinged(DevicePinged devicePinged) {
      if (isInactive()) {
        activate();
        makeHappy();
      }
      return this;
    }
  }

  static final String projectionShardsPerZoom = "woe.twin.projection-shards-per-zoom";
  private static final String tagFormat = "zoom-%d-tag-%d";

  private Set<String> tagsForEntity() {
    int numberOfShards = actorContext.getSystem().settings().config().getInt(projectionShardsPerZoom);
    return tagsFor(region, numberOfShards);
  }

  static Set<String> tagsFor(WorldMap.Region region, int numberOfShards) {
    final HashSet<String> tags = new HashSet<>();
    IntStream.rangeClosed(3, region.zoom).forEach(zoom -> {
      final String entityId = entityIdOf(regionAtLatLng(zoom, atCenter(region)));
      tags.add(String.format(tagFormat, zoom, Math.abs(entityId.hashCode()) % numberOfShards));
    });
    return tags;
  }

  static List<String> tagsAll(ActorSystem<?> actorSystem) {
    final List<String> tags = new ArrayList<>();
    final int numberOfShards = actorSystem.settings().config().getInt(projectionShardsPerZoom);
    IntStream.rangeClosed(3, 18).forEach(zoom ->
        IntStream.range(0, numberOfShards).forEach(tagId ->
            tags.add(String.format(tagFormat, zoom, tagId))));
    return tags;
  }

  private Logger log() {
    return actorContext.getSystem().log();
  }
}
