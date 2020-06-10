package oti.twin;

import akka.actor.typed.ActorRef;
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
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class Region extends EventSourcedBehavior<Region.Command, Region.Event, Region.State> {
  final String entityId;
  final WorldMap.Region region;
  final Set<String> tags;
  final ClusterSharding clusterSharding;
  final ActorContext<Command> actorContext;
  static final EntityTypeKey<Command> entityTypeKey = EntityTypeKey.create(Command.class, Region.class.getSimpleName());

  static Behavior<Command> create(String entityId, ClusterSharding clusterSharding) {
    return Behaviors.setup(actorContext -> new Region(entityId, clusterSharding, actorContext));
  }

  private Region(String entityId, ClusterSharding clusterSharding, ActorContext<Command> actorContext) {
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
        .onCommand(SelectionCommand.class, this::onAddSelection)
        .build();
  }

  @Override
  public EventHandler<State, Event> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(SelectionAccepted.class, State::addSelection)
        .build();
  }

  @Override
  public Set<String> tagsFor(Event event) {
    return tags;
  }

  private Effect<Event, State> onAddSelection(State state, SelectionCommand selectionCommand) {
    if (state.isNewSelection(selectionCommand)) {
      log().debug("{} accepted {}", region, selectionCommand);
      SelectionAccepted selectionAccepted = new SelectionAccepted(selectionCommand);
      return Effect().persist(selectionAccepted)
          .thenRun(newState -> eventReply(newState, selectionAccepted));
    } else {
      eventReply(state, new SelectionNoOverlap(selectionCommand));
      return Effect().none();
    }
  }

  private void eventReply(State state, SelectionEvent selectionEvent) {
    selectionEvent.replyTo.tell(selectionEvent);
  }

  interface Command extends CborSerializable {
  }

  abstract static class SelectionCommand implements Command {
    final Action action;
    final WorldMap.Region region;
    final ActorRef<Event> replyTo;

    enum Action {
      create, delete, happy, sad
    }

    SelectionCommand(Action action, WorldMap.Region region, ActorRef<Event> replyTo) {
      this.action = action;
      this.region = region;
      this.replyTo = replyTo;
    }

    @Override
    public String toString() {
      return String.format("%s[%s, %s]", getClass().getSimpleName(), action, region);
    }
  }

  static final class SelectionCreate extends SelectionCommand {
    SelectionCreate(WorldMap.Region region, ActorRef<Event> replyTo) {
      super(Action.create, region, replyTo);
    }
  }

  static final class SelectionDelete extends SelectionCommand {
    SelectionDelete(WorldMap.Region region, ActorRef<Event> replyTo) {
      super(Action.delete, region, replyTo);
    }
  }

  static final class SelectionHappy extends SelectionCommand {
    SelectionHappy(WorldMap.Region region, ActorRef<Event> replyTo) {
      super(Action.happy, region, replyTo);
    }
  }

  static final class SelectionSad extends SelectionCommand {
    SelectionSad(WorldMap.Region region, ActorRef<Event> replyTo) {
      super(Action.sad, region, replyTo);
    }
  }

  interface Event extends CborSerializable {
  }

  abstract static class SelectionEvent implements Event {
    final SelectionCommand.Action action;
    final WorldMap.Region region;
    final ActorRef<Event> replyTo;
    final Status status;

    enum Status {
      accepted, noOverlap, failed
    }

    SelectionEvent(SelectionCommand.Action action, WorldMap.Region region, ActorRef<Event> replyTo, Status status) {
      this.action = action;
      this.region = region;
      this.replyTo = replyTo;
      this.status = status;
    }

    @Override
    public String toString() {
      return String.format("%s[%s, %s, %s, %s]", getClass().getSimpleName(), action, region, replyTo, status);
    }
  }

  static final class SelectionAccepted extends SelectionEvent {
    SelectionAccepted(SelectionCommand selectionCommand) {
      super(selectionCommand.action, selectionCommand.region, selectionCommand.replyTo, Status.accepted);
    }
  }

  static final class SelectionNoOverlap extends SelectionEvent {
    SelectionNoOverlap(SelectionCommand selectionCommand) {
      super(selectionCommand.action, selectionCommand.region, selectionCommand.replyTo, Status.noOverlap);
    }
  }

  static final class SelectionFailed extends SelectionEvent {
    SelectionFailed(SelectionCommand selectionCommand) {
      super(selectionCommand.action, selectionCommand.region, selectionCommand.replyTo, Status.failed);
    }
  }

  private Set<String> tagsForEntity() {
    int numberOfShards = actorContext.getSystem().settings().config().getInt("akka.cluster.sharding.number-of-shards");
    return Collections.singleton(String.format("region-%d", entityId.hashCode() % numberOfShards));
  }

  static final class State implements CborSerializable {
    enum Status {
      happy, sad, neutral
    }

    final WorldMap.Region region;
    final Selections selections;
    Status status;

    State(WorldMap.Region region) {
      this.region = region;
      selections = new Selections(region);
      status = region.isDevice() ? Status.happy : Status.neutral;
    }

    boolean isNewSelection(SelectionCommand selectionCommand) {
      switch (selectionCommand.action) {
        case create:
          return selections.isContainedWithin(selectionCommand.region) || selections.isContainerOfVisible(selectionCommand.region);
        case delete:
        case happy:
        case sad:
          return selections.isContainedWithin(selectionCommand.region) || selections.isContainerOf(selectionCommand.region);
        default:
          return false;
      }
    }

    State addSelection(SelectionAccepted selectionAccepted) {
      switch (selectionAccepted.action) {
        case create:
          selections.create(selectionAccepted.region);
          break;
        case delete:
          selections.delete(selectionAccepted.region);
          break;
        case happy:
          status = region.isDevice() ? Status.happy : Status.neutral;
          break;
        case sad:
          status = region.isDevice() ? Status.sad : Status.neutral;
      }
      return this;
    }
  }

  static final class Selections implements CborSerializable {
    final WorldMap.Region region;
    final List<WorldMap.Region> currentSelections = new ArrayList<>();

    Selections(WorldMap.Region region) {
      this.region = region;
    }

    void create(WorldMap.Region regionCreate) {
      if (isContainedWithin(regionCreate)) {
        currentSelections.clear();
        currentSelections.add(regionCreate);
      } else if (isContainerOf(regionCreate)) {
        currentSelections.removeIf(regionCreate::contains);
        currentSelections.add(regionCreate);
      }
    }

    void delete(WorldMap.Region regionDelete) {
      if (isContainedWithin(regionDelete)) {
        currentSelections.clear();
      } else if (isContainerOf(regionDelete)) {
        currentSelections.removeIf(regionDelete::contains);
      }
    }

    boolean isContainedWithin(WorldMap.Region region) {
      return region.contains(this.region);
    }

    boolean isContainerOf(WorldMap.Region region) {
      return this.region.contains(region);
    }

    boolean isContainerOfVisible(WorldMap.Region region) {
      return isContainerOf(region) && isVisible(region);
    }

    private boolean isVisible(WorldMap.Region region) {
      return currentSelections.stream().noneMatch(currentRegion -> currentRegion.contains(region));
    }
  }

  Logger log() {
    return actorContext.getSystem().log();
  }
}
