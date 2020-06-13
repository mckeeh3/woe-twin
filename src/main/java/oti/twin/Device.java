package oti.twin;

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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

import static oti.twin.WorldMap.*;

class Device extends EventSourcedBehavior<Device.Command, Device.Event, Device.State> {
  final String entityId;
  final WorldMap.Region region;
  final Set<String> tags;
  final ClusterSharding clusterSharding;
  final ActorContext<Device.Command> actorContext;
  static final EntityTypeKey<Region.Command> entityTypeKey = EntityTypeKey.create(Region.Command.class, Region.class.getSimpleName());

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
      return Effect().persist(new DeviceActivated(telemetryCreateCommand.region));
    }
    return Effect().none();
  }

  private Effect<Event, State> onDeleteCommand(State state, TelemetryDeleteCommand telemetryDeleteCommand) {
    if (state.isActive()) {
      return Effect().persist(new DeviceDeactivated(telemetryDeleteCommand.region));
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
    return Effect().none();
  }

  @Override
  public EventHandler<State, Event> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(DeviceActivated.class, State::deviceActivated)
        .onEvent(DeviceDeactivated.class, State::deviceDeactivated)
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

  abstract static class TelemetryCommand implements Command {
    final Action action;
    final WorldMap.Region region;

    enum Action {
      create, delete, happy, sad, ping
    }

    TelemetryCommand(Action action, WorldMap.Region region) {
      this.action = action;
      this.region = region;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TelemetryCommand that = (TelemetryCommand) o;
      return action == that.action &&
          region.equals(that.region);
    }

    @Override
    public int hashCode() {
      return Objects.hash(action, region);
    }

    @Override
    public String toString() {
      return String.format("%s[%s, %s]", getClass().getSimpleName(), action, region);
    }
  }

  static class TelemetryCreateCommand extends TelemetryCommand {
    TelemetryCreateCommand(WorldMap.Region region) {
      super(Action.create, region);
    }
  }

  static class TelemetryDeleteCommand extends TelemetryCommand {
    TelemetryDeleteCommand(WorldMap.Region region) {
      super(Action.delete, region);
    }
  }

  static class TelemetryHappyCommand extends TelemetryCommand {
    TelemetryHappyCommand(WorldMap.Region region) {
      super(Action.happy, region);
    }
  }

  static class TelemetrySadCommand extends TelemetryCommand {
    TelemetrySadCommand(WorldMap.Region region) {
      super(Action.sad, region);
    }
  }

  static class TelemetryPingCommand extends TelemetryCommand {
    TelemetryPingCommand(WorldMap.Region region) {
      super(Action.ping, region);
    }
  }

  interface Event extends CborSerializable {
  }

  abstract static class DeviceEvent implements Event {
    final WorldMap.Region region;

    DeviceEvent(WorldMap.Region region) {
      this.region = region;
    }

    @Override
    public String toString() {
      return String.format("%s[%s]", getClass().getSimpleName(), region);
    }
  }

  static class DeviceActivated extends DeviceEvent {
    DeviceActivated(WorldMap.Region region) {
      super(region);
    }
  }

  static class DeviceDeactivated extends DeviceEvent {
    DeviceDeactivated(WorldMap.Region region) {
      super(region);
    }
  }

  static class DeviceMadeHappy extends DeviceEvent {
    DeviceMadeHappy(WorldMap.Region region) {
      super(region);
    }
  }

  static class DeviceMadeSad extends DeviceEvent {
    DeviceMadeSad(WorldMap.Region region) {
      super(region);
    }
  }

  static class DevicePinged extends DeviceEvent {
    DevicePinged(WorldMap.Region region) {
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

    public State deviceDeactivated(DeviceDeactivated deviceDeactivated) {
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
      // TODO this might not be worthy of an event
      return this;
    }
  }

  private Set<String> tagsForEntity() {
    int numberOfShards = actorContext.getSystem().settings().config().getInt("akka.cluster.sharding.number-of-shards");
    return tagsFor(region, numberOfShards);
  }

  static Set<String> tagsFor(WorldMap.Region region, int numberOfShards) {
    final HashSet<String> tags = new HashSet<>();
    IntStream.rangeClosed(3, region.zoom).forEach(zoom -> {
      final String entityId = entityIdOf(regionAtLatLng(zoom, atCenter(region)));
      tags.add(String.format("zoom-%d-entity-%d", zoom, entityId.hashCode() % numberOfShards));
    });
    return tags;
  }
}
