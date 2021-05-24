package woe.twin;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.SnapshotSelectionCriteria;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import akka.persistence.typed.javadsl.Recovery;

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
        .onCommand(Passivate.class, this::onPassivate)
        .build();
  }

  private Effect<Event, State> onCreateCommand(State state, TelemetryCreateCommand telemetryCreateCommand) {
    if (state.isInactive()) {
      log().info("{}", telemetryCreateCommand);
      return Effect().persist(new DeviceActivated(telemetryCreateCommand.region))
          .thenReply(telemetryCreateCommand.replyTo, s -> new TelemetryCreateResponse(telemetryCreateCommand));
    } else {
      telemetryCreateCommand.replyTo.tell(new TelemetryCreateResponse(telemetryCreateCommand));
      return Effect().none();
    }
  }

  private Effect<Event, State> onDeleteCommand(State state, TelemetryDeleteCommand telemetryDeleteCommand) {
    if (state.isActive()) {
      if (state.isHappy()) {
        return Effect().persist(new DeviceDeactivatedHappy(telemetryDeleteCommand.region))
            .thenReply(telemetryDeleteCommand.replyTo, s -> new TelemetryDeleteResponse(telemetryDeleteCommand));
      } else {
        return Effect().persist(new DeviceDeactivatedSad(telemetryDeleteCommand.region))
            .thenReply(telemetryDeleteCommand.replyTo, s -> new TelemetryDeleteResponse(telemetryDeleteCommand));
      }
    } else {
      telemetryDeleteCommand.replyTo.tell(new TelemetryDeleteResponse(telemetryDeleteCommand));
      return Effect().none();
    }
  }

  private Effect<Event, State> onHappyCommand(State state, TelemetryHappyCommand telemetryHappyCommand) {
    if (state.isActive() && state.isSad()) {
      return Effect().persist(new DeviceMadeHappy(telemetryHappyCommand.region))
          .thenReply(telemetryHappyCommand.replyTo, s -> new TelemetryHappyResponse(telemetryHappyCommand));
    } else {
      telemetryHappyCommand.replyTo.tell(new TelemetryHappyResponse(telemetryHappyCommand));
      return Effect().none();
    }
  }

  private Effect<Event, State> onSadCommand(State state, TelemetrySadCommand telemetrySadCommand) {
    if (state.isActive() && state.isHappy()) {
      return Effect().persist(new DeviceMadeSad(telemetrySadCommand.region))
          .thenReply(telemetrySadCommand.replyTo, s -> new TelemetrySadResponse(telemetrySadCommand));
    } else {
      telemetrySadCommand.replyTo.tell(new TelemetrySadResponse(telemetrySadCommand));
      return Effect().none();
    }
  }

  private Effect<Event, State> onPingCommand(State state, TelemetryPingCommand telemetryPingCommand) {
    if (state.isInactive()) {
      log().info("Ping create inactive device {}", telemetryPingCommand);
      return Effect().persist(new DeviceActivated(telemetryPingCommand.region))
          .thenReply(telemetryPingCommand.replyTo, s -> new TelemetryPingResponse(telemetryPingCommand));
    } else {
      telemetryPingCommand.replyTo.tell(new TelemetryPingResponse(telemetryPingCommand));
      return Effect().none();
    }
  }

  private Effect<Event, State> onPassivate(State state, Passivate passivate) {
    log().info("Stop entity {}", entityId);
    return Effect().none();
  }

  @Override
  public Recovery recovery() {
    log().info("Start entity {}", entityId);
    return Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.none());
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
    public final ActorRef<TelemetryResponse> replyTo;

    public TelemetryCommand(WorldMap.Region region, ActorRef<TelemetryResponse> replyTo) {
      this.region = region;
      this.replyTo = replyTo;
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
    public TelemetryCreateCommand(@JsonProperty("region") WorldMap.Region region, @JsonProperty("replyTo") ActorRef<TelemetryResponse> replyTo) {
      super(region, replyTo);
    }
  }

  public static class TelemetryDeleteCommand extends TelemetryCommand {
    @JsonCreator
    public TelemetryDeleteCommand(@JsonProperty("region") WorldMap.Region region, @JsonProperty("replyTo") ActorRef<TelemetryResponse> replyTo) {
      super(region, replyTo);
    }
  }

  public static class TelemetryHappyCommand extends TelemetryCommand {
    @JsonCreator
    public TelemetryHappyCommand(@JsonProperty("region") WorldMap.Region region, @JsonProperty("replyTo") ActorRef<TelemetryResponse> replyTo) {
      super(region, replyTo);
    }
  }

  public static class TelemetrySadCommand extends TelemetryCommand {
    @JsonCreator
    public TelemetrySadCommand(@JsonProperty("region") WorldMap.Region region, @JsonProperty("replyTo") ActorRef<TelemetryResponse> replyTo) {
      super(region, replyTo);
    }
  }

  public static class TelemetryPingCommand extends TelemetryCommand {
    @JsonCreator
    public TelemetryPingCommand(@JsonProperty("region") WorldMap.Region region, @JsonProperty("replyTo") ActorRef<TelemetryResponse> replyTo) {
      super(region, replyTo);
    }
  }

  public enum Passivate implements Command {
    INSTANCE
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

  interface Response extends CborSerializable {
  }

  public abstract static class TelemetryResponse implements Response {
    public final TelemetryCommand telemetryCommand;

    protected TelemetryResponse(TelemetryCommand telemetryCommand) {
      this.telemetryCommand = telemetryCommand;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TelemetryResponse that = (TelemetryResponse) o;
      return telemetryCommand.equals(that.telemetryCommand);
    }

    @Override
    public int hashCode() {
      return Objects.hash(telemetryCommand);
    }
  }

  public static class TelemetryCreateResponse extends TelemetryResponse {
    @JsonCreator
    protected TelemetryCreateResponse(TelemetryCreateCommand telemetryCommand) {
      super(telemetryCommand);
    }
  }

  public static class TelemetryDeleteResponse extends TelemetryResponse {
    @JsonCreator
    protected TelemetryDeleteResponse(TelemetryDeleteCommand telemetryCommand) {
      super(telemetryCommand);
    }
  }

  public static class TelemetryHappyResponse extends TelemetryResponse {
    @JsonCreator
    protected TelemetryHappyResponse(TelemetryHappyCommand telemetryCommand) {
      super(telemetryCommand);
    }
  }

  public static class TelemetrySadResponse extends TelemetryResponse {
    @JsonCreator
    protected TelemetrySadResponse(TelemetrySadCommand telemetryCommand) {
      super(telemetryCommand);
    }
  }

  public static class TelemetryPingResponse extends TelemetryResponse {
    @JsonCreator
    protected TelemetryPingResponse(TelemetryPingCommand telemetryCommand) {
      super(telemetryCommand);
    }
  }

  static final String entityTagsSetting = "woe.twin.entity-tags";

  private Set<String> tagsForEntity() {
    final var entityTagsCount = actorContext.getSystem().settings().config().getInt(entityTagsSetting);
    return tagsFor(region, entityTagsCount);
  }

  static Set<String> tagsFor(WorldMap.Region region, int numberOfShards) {
    final var entityId = entityIdOf(region);
    return Collections.singleton("" + Math.abs(entityId.hashCode()) % numberOfShards);
  }

  static List<String> tagsAll(ActorSystem<?> actorSystem) {
    final var tags = new ArrayList<String>();
    final var entityTagsCount = actorSystem.settings().config().getInt(entityTagsSetting);
    IntStream.range(0, entityTagsCount).forEach(tagId -> tags.add(String.format("%d", tagId)));
    return tags;
  }

  private Logger log() {
    return actorContext.getSystem().log();
  }
}
