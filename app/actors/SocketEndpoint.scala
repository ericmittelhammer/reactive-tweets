package actors

import play.api.libs.iteratee.{ Iteratee, Enumerator }
import play.api.libs.concurrent.Execution.Implicits._

import play.api.libs.json.{ JsValue, JsString }

import akka.actor.{ Props, ActorRef, Actor, ActorRefFactory }
import akka.event.Logging

object SocketEndpoint {

  /**
   * Creates a SocketEndpoint.
   * ActorRef: reference to the supervisor actor
   * ActorRefFactory: the resulting parent context of the retured Actor
   * Option[String]: An optional name to be used in locating the socket
   * @return a SocketEndPoint ActorRef
   */
  type SocketEndpointFactory = (ActorRef, ActorRefFactory, Option[String]) => ActorRef

  case class NewMessage(message: MessageStream.Message)

  def props(supervisor: ActorRef) = Props(classOf[SocketEndpoint], supervisor)

}

/**
 * Handles all IO operations for a websocket.
 * Sending a [[Supervisor.NewSocket]] message will create the in/out
 * Iteratee and Enumerator and reply to sender with them.
 * @constructor create a new SocketEndpoint with a reference to the supervisor actor
 * @param supervisor a referne to the supervisor
 */
class SocketEndpoint(supervisor: ActorRef) extends Actor {

  val log = Logging(context.system, this)

  var out: Enumerator[MessageStream.Message] = Enumerator.empty

  var in: Iteratee[JsValue, Unit] = _

  var filterString: Option[String] = None

  def receive = {

    case Supervisor.NewSocket(name: Option[String]) => {

      // create the iteratee that will handle incoming data from the websocket 
      in = Iteratee.foreach[JsValue] { msg =>
        msg \ "messageType" match {
          case JsString("newMessage") => supervisor ! MessageStream.NewMessage((msg \ "payload").as[String])
          case JsString("filter") => filterString = Some((msg \ "value").as[String])
          case _ => Unit
        }
      }.map { _ => // this will map over the Iteratee once it has recieved EOF
        supervisor ! Supervisor.SocketClosed(self)
      }

      // send the Iteratee and Enumerator back
      sender ! (in, out)

      // start handling messages
      context.become(connected)

    }

    case SocketEndpoint.NewMessage(message: MessageStream.Message) =>
      log.warning("not connected yet")

  }

  def connected: Receive = {

    case SocketEndpoint.NewMessage(message: MessageStream.Message) =>
      out = out >>> Enumerator(message)

    case Supervisor.NewSocket(name: Option[String]) => log.warning("already connected")
  }
}
