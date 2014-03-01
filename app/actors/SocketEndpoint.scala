package actors

import play.api.libs.ws.WS
import play.api.libs.iteratee.{ Iteratee, Enumerator, Input, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._

import play.api.libs.json.{ JsValue, JsString }

import akka.actor.{ Props, ActorRef, Actor }
import akka.event.EventStream

object SocketEndpoint {

  case class NewMessage(message: MessageStream.Message)

  def props(supervisor: ActorRef) = Props(classOf[SocketEndpoint], supervisor)

}

class SocketEndpoint(supervisor: ActorRef) extends Actor {

  import SocketEndpoint._

  var out: Enumerator[MessageStream.Message] = Enumerator.empty

  var in: Iteratee[JsValue, Unit] = _

  var filterString: Option[String] = None

  def receive = {

    case Supervisor.NewSocket() => {

      in = Iteratee.foreach[JsValue] { msg =>
        msg \ "messageType" match {
          case j @ JsString("newMessage") => supervisor ! MessageStream.NewMessage((j \ "payload").as[String])
          case j @ JsString("filter") => filterString = Some((j \ "value").as[String])
          case _ => Unit
        }
      }.map { _ =>
        supervisor ! Supervisor.SocketClosed(self)
      }
    }

    case NewMessage(message: MessageStream.Message) => out = out >>> Enumerator(message)

  }
}

trait SocketEndpointFactory {
  def newSocketEndpoint: ActorRef
}