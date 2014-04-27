package actors

import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._

import play.api.libs.json.{ JsValue, JsString, JsPath, JsResult, JsSuccess, JsError }
import play.api.libs.json.{ Reads, Writes }
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

import akka.actor.{ Props, ActorRef, Actor, ActorRefFactory }
import akka.event.{ Logging, LoggingReceive }

import java.util.Date
import java.text.DateFormat

object SocketEndpoint {

  /**
   * Creates a SocketEndpoint.
   * ActorRef: reference to the supervisor actor
   * ActorRefFactory: the resulting parent context of the retured Actor
   * Option[String]: An optional name to be used in locating the socket
   * @return a SocketEndPoint ActorRef
   */
  type SocketEndpointFactory = (ActorRefFactory, ActorRef, String) => ActorRef

  case class NewMessage(message: MessageStream.Message)

  def props(supervisor: ActorRef, name: String): Props = Props(classOf[SocketEndpoint], supervisor, name)

}

/**
 * Handles all IO operations for a websocket.
 * Sending a [[Supervisor.NewSocket]] message will create the in/out
 * Iteratee and Enumerator and reply to sender with them.
 * @constructor create a new SocketEndpoint with a reference to the supervisor actor
 * @param supervisor a reference to the supervisor
 * @name the name of the user bound to this socket
 */
class SocketEndpoint(supervisor: ActorRef, name: String) extends Actor {

  import SocketEndpoint._

  val log = Logging(context.system, this)

  // create the enumerator and the channel that will push to it
  val (out, channel) = Concurrent.broadcast[JsValue]

  var filterString: Option[String] = None

  // will convert a message back to json to be written to the stream
  val messageWrites: Writes[MessageStream.Message] = (
    // write the timestamp out in RFC2822 time
    (JsPath \ "timestamp").write[Date](Writes.dateWrites("EEE, d MMM yyyy HH:mm:ss Z")) and
    (JsPath \ "author").write[String] and
    (JsPath \ "message").write[String]
  )(unlift(MessageStream.Message.unapply))

  def receive: Receive = LoggingReceive {

    case Supervisor.NewSocket(name: String) => {

      // create the iteratee that will handle incoming data from the websocket
      var in: Iteratee[JsValue, Unit] = Iteratee.foreach[JsValue] { msg =>

        msg \ "messageType" match {

          case JsString("newMessage") => {
            val message = MessageStream.Message(new java.util.Date, name, (msg \ "payload" \ "message").as[String])
            supervisor ! SocketEndpoint.NewMessage(message)
          }

          case JsString("filter") => filterString = Some((msg \ "value").as[String])

          case _ => Unit
        }
      }.map { _ => // this will map over the Iteratee once it has received EOF
        //let the supervisor know we're done & stop
        supervisor ! Supervisor.SocketClosed(self)
        context.stop(self)
      }

      // send the Iteratee and Enumerator back
      sender ! (in, out)

      // start handling messages
      context.become(connected)

    }

    case SocketEndpoint.NewMessage(message: MessageStream.Message) =>
      log.warning("not connected yet")

  }

  def connected: Receive = LoggingReceive {

    case SocketEndpoint.NewMessage(message: MessageStream.Message) =>
      channel.push(messageWrites.writes(message))

    case Supervisor.NewSocket(name: String) => log.warning("already connected")
  }
}
