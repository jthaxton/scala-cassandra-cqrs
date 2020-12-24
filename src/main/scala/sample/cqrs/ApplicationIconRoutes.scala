package sample.cqrs

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import akka.util.Timeout

object ApplicationIconRoutes {
  final case class ChangePosition(applicationIconId: String, x: Int, y: Int)
  final case class ChangeNickname(applicationIconId: String, nickname: String)
}

class ApplicationIconRoutes()(implicit system: ActorSystem[_]) {

  implicit private val timeout: Timeout =
    Timeout.create(system.settings.config.getDuration("icon.askTimeout"))
  private val sharding = ClusterSharding(system)

  import ApplicationIconRoutes._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import akka.http.scaladsl.server.Directives._
  import ApplicationIconJsonFormats._

  val icon: Route =
    pathPrefix("app") {
      pathPrefix("icon") {
        concat(
          post {
            entity(as[ChangePosition]) {
              data =>
                val entityRef =
                  sharding.entityRefFor(ApplicationIcon.EntityKey, data.applicationIconId)
                val reply: Future[StatusReply[ApplicationIcon.Summary]] =
                  entityRef.ask(ApplicationIcon.ChangePosition(data.applicationIconId, data.x, data.y, _))
                onSuccess(reply) {
                  case StatusReply.Success(summary: ApplicationIcon.Summary) =>
                    complete(StatusCodes.OK -> summary)
                  case StatusReply.Error(reason) =>
                    complete(StatusCodes.BadRequest -> reason)
                }
            }
          },
//          put {
//            entity(as[UpdateItem]) {
//              data =>
//                val entityRef =
//                  sharding.entityRefFor(ShoppingCart.EntityKey, data.cartId)
//
//                def command(replyTo: ActorRef[StatusReply[ShoppingCart.Summary]]) =
//                  if (data.quantity == 0)
//                    ShoppingCart.RemoveItem(data.itemId, replyTo)
//                  else
//                    ShoppingCart.AdjustItemQuantity(data.itemId, data.quantity, replyTo)
//
//                val reply: Future[StatusReply[ShoppingCart.Summary]] =
//                  entityRef.ask(command(_))
//                onSuccess(reply) {
//                  case StatusReply.Success(summary: ShoppingCart.Summary) =>
//                    complete(StatusCodes.OK -> summary)
//                  case StatusReply.Error(reason) =>
//                    complete(StatusCodes.BadRequest -> reason)
//                }
//            }
//          },
//          pathPrefix(Segment) { cartId =>
//            concat(get {
//              val entityRef =
//                sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
//              onSuccess(entityRef.ask(ShoppingCart.Get)) { summary =>
//                if (summary.items.isEmpty) complete(StatusCodes.NotFound)
//                else complete(summary)
//              }
//            }, path("checkout") {
//              post {
//                val entityRef =
//                  sharding.entityRefFor(ShoppingCart.EntityKey, cartId)
//                val reply: Future[StatusReply[ShoppingCart.Summary]] =
//                  entityRef.ask(ShoppingCart.Checkout(_))
//                onSuccess(reply) {
//                  case StatusReply.Success(summary: ShoppingCart.Summary) =>
//                    complete(StatusCodes.OK -> summary)
//                  case StatusReply.Error(reason) =>
//                    complete(StatusCodes.BadRequest -> reason)
//                }
//              }
//            })
//          }
        )
      }
    }

}

object ApplicationIconJsonFormats {

  import spray.json.RootJsonFormat
  // import the default encoders for primitive types (Int, String, Lists etc)
  import spray.json.DefaultJsonProtocol._

  implicit val summaryFormat: RootJsonFormat[ApplicationIcon.Summary] =
    jsonFormat2(ApplicationIcon.Summary)
  implicit val changePositionFormat: RootJsonFormat[ApplicationIconRoutes.ChangePosition] =
    jsonFormat3(ApplicationIconRoutes.ChangePosition)
//  implicit val changeNicknameFormat: RootJsonFormat[ApplicationIconRoutes.ChangeNickname] =
//    jsonFormat3(ApplicationIconRoutes.ChangeNickname)

}
