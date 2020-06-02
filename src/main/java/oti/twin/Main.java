package oti.twin;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Behaviors;

public class Main {
  public static Behavior<Void> create() {
    return Behaviors.setup(
        context -> {

          return Behaviors.receive(Void.class)
              .onSignal(Terminated.class, signal -> Behaviors.stopped())
              .build();
        }
    );
  }

  public static void main(String[] args) {
    ActorSystem.create(Main.create(), "OTI Twin");
  }
}
