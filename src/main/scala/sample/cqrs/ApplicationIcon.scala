package sample.cqrs

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}

object ApplicationIcon {

  final case class State(x: Int, y: Int, name: String, nickname: String) extends CborSerializable {
    def changePosition(applicationIconId: String, incomingX: Int, incomingY: Int): State = {
      copy(x = incomingX)
      copy(y = incomingY)
    }
    def changeNickname(applicationIconId: String, incomingNickname: String): State = {
      copy(nickname = incomingNickname)
    }

    def toSummary: Summary = Summary(x, y, name, nickname)
  }

  object State {
    val empty: State = State(x = 0, y = 0, name = "", nickname = "")
  }

  final case class Get(replyTo: ActorRef[Summary]) extends Command
  final case class ChangePosition(x: Int, y: Int, replyTo: ActorRef[StatusReply[Summary]]) extends Command {}
  final case class ChangeName(name: String, replyTo: ActorRef[StatusReply[Summary]]) extends Command {}
  final case class ChangeNickname(nickname: String, replyTo: ActorRef[StatusReply[Summary]]) extends Command {}
  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("ApplicationIcon")

  def init(system: ActorSystem[_], eventProcessorSettings: EventProcessorSettings): Unit = {
    ClusterSharding(system).init(Entity(EntityKey) { entityContext =>
      val n = math.abs(entityContext.entityId.hashCode % eventProcessorSettings.parallelism)
      val eventProcessorTag = eventProcessorSettings.tagPrefix + "-" + n
      ApplicationIcon(entityContext.entityId, Set(eventProcessorTag))
    }.withRole("write-model"))
  }

  final case class Summary(x: Int, y: Int, name: String, nickname: String) extends CborSerializable

  sealed trait Event extends CborSerializable {
    def applicationIconId: String
  }

  final case class PositionChanged(applicationIconId: String, x: Int, y: Int) extends Event
  final case class NicknameChanged(applicationIconId: String, nickname: String) extends Event


  def apply(applicationIconId: String, eventProcessorTags: Set[String]): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        PersistenceId(EntityKey.name, applicationIconId),
        State.empty,
        (state, command) =>
          //The shopping cart behavior changes if it's checked out or not.
          // The commands are handled differently for each case.
          changePosition(applicationIconId, state, command),
        (state, event) => handleEvent(state, event))
      .withTagger(_ => eventProcessorTags)
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
//      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }

  private def changePosition(applicationIconId: String, state: State, command: Command): ReplyEffect[Event, State] = {
    command match {
      case Get(replyTo) =>
        Effect.reply(replyTo)(state.toSummary)
      case ChangePosition( x, y, replyTo) =>
        Effect
          .persist(PositionChanged(applicationIconId, x, y))
          .thenReply(replyTo)(updatedApplicationIcon => StatusReply.Success(updatedApplicationIcon.toSummary))
      case cmd: ChangeNickname =>
        Effect.reply(cmd.replyTo)(StatusReply.error("BAD"))
    }
  }

  private def handleEvent(state: State, event: Event) = {
    event match {
      case PositionChanged(applicationIconId, x, y)        => state.changePosition(applicationIconId, x, y)
//      case NicknameChanged(applicationIconId, nickname)    => state.changeNickname(applicationIconId, nickname)
    }
  }
}
