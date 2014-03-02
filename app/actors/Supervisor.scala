package actors

import play.api.libs.ws.WS
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current

import akka.actor.{ Props, ActorRef, Actor, ActorRefFactory }
import akka.event.{ EventStream, Logging, LoggingReceive }

import scala.collection.parallel.ParSet

import actors.MessageStream.MessageStreamFactory
import actors.SocketEndpoint.SocketEndpointFactory

object Supervisor {

  case class NewSocket(name: Option[String] = None)

  case class SocketClosed(closedSocket: ActorRef)

  def props(messageStreamFactory: MessageStreamFactory,
    socketEndpointFactory: SocketEndpointFactory) =
    Props(classOf[Supervisor], messageStreamFactory, socketEndpointFactory)

}

/**
 * Supervisor that will route messages between the MessageStream and SocketEndpoints
 * @param messageStream a factory funtction to create MessageStream actors.
 * The funciton accepts a reference to it's supervisor
 */
class Supervisor(messageStreamFactory: MessageStreamFactory,
    socketEndpointFactory: SocketEndpointFactory) extends Actor {

  import Supervisor._

  val log = Logging(context.system, this)

  val messageStream = messageStreamFactory(self, context.system)

  var sockets = ParSet[ActorRef]()

  def receive = LoggingReceive {

    case m @ NewSocket(name: Option[String]) => {

      val newSocket: ActorRef = socketEndpointFactory(self, context.system, name)

      newSocket forward m

      sockets = sockets + newSocket

      log.info(s"connected socket: ${newSocket.path}")

      if (sockets.size == 1) {
        log.info("starting message stream")
        messageStream ! MessageStream.StartStream()
      }
    }

    case SocketClosed(closedSocket: ActorRef) => {

      sockets = sockets - closedSocket

      log.info(s"closed socket: ${closedSocket.path}")

      if (sockets.size == 0) {
        log.info("no connected sockets, shutting down message stream")
        messageStream ! MessageStream.StopStream()
      }
    }

    case m @ MessageStream.NewMessage(message: MessageStream.Message) => {
      sockets foreach (_ forward m)
    }

  }
}