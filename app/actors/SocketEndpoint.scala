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
  type SocketEndpointFactory = (ActorRef, ActorRefFactory, Option[String]) => ActorRef

  case class NewMessage(message: MessageStream.Message)

  def props(supervisor: ActorRef): Props = Props(classOf[SocketEndpoint], supervisor)

  // used to convert from raw json coming from the socket into the message type
  val inputReads: Reads[MessageStream.Message] = (
    (JsPath \ "author").read[String] and
    (JsPath \ "message").read[String]
  //partially apply the case class apply function with the current timestamp
  )(MessageStream.Message.apply(new java.util.Date(), _: String, _: String))

  def inputToMessageResult(payload: JsValue): JsResult[MessageStream.Message] =
    payload.validate[MessageStream.Message](inputReads)

  // will convert a message back to json to be written to the stream
  val messageWrites: Writes[MessageStream.Message] = (
    // write the timestamp out in RFC2822 time
    (JsPath \ "timestamp").write[Date](Writes.dateWrites("EEE, d MMM yyyy HH:mm:ss Z")) and
    (JsPath \ "author").write[String] and
    (JsPath \ "message").write[String]
  )(unlift(MessageStream.Message.unapply))
}

/**
 * Handles all IO operations for a websocket.
 * Sending a [[Supervisor.NewSocket]] message will create the in/out
 * Iteratee and Enumerator and reply to sender with them.
 * @constructor create a new SocketEndpoint with a reference to the supervisor actor
 * @param supervisor a referne to the supervisor
 */
class SocketEndpoint(supervisor: ActorRef) extends Actor {

  import SocketEndpoint._

  val log = Logging(context.system, this)

  // create the enumerator and the channel that will push to it
  val (out, channel) = Concurrent.broadcast[JsValue]

  var filterString: Option[String] = None

  def receive: Receive = LoggingReceive {

    case Supervisor.NewSocket(name: Option[String]) => {

      // create the iteratee that will handle incoming data from the websocket
      var in: Iteratee[JsValue, Unit] = Iteratee.foreach[JsValue] { msg =>

        msg \ "messageType" match {

          case JsString("newMessage") =>

            //try to parse the json into a Message
            inputToMessageResult((msg \ "payload").as[JsValue]) match {

              // if it validates, send the message to the supervisor
              case s: JsSuccess[MessageStream.Message] => supervisor ! SocketEndpoint.NewMessage(s.get)
              case e: JsError => // if there was a parsing error, do nothing
            }

          case JsString("filter") => filterString = Some((msg \ "value").as[String])

          case _ => Unit
        }
      }.map { _ => // this will map over the Iteratee once it has received EOF
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

  def connected: Receive = LoggingReceive {

    case SocketEndpoint.NewMessage(message: MessageStream.Message) =>
      channel.push(messageWrites.writes(message))

    case Supervisor.NewSocket(name: Option[String]) => log.warning("already connected")
  }
}
