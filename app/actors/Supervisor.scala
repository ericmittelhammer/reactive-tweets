package actors

import play.api.libs.ws.WS
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current

import akka.actor.{ Props, ActorRef, Actor, ActorRefFactory }
import akka.event.{ EventStream, Logging, LoggingReceive }
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy._

import scala.collection.parallel.ParSet
import scala.concurrent.duration._

import actors.MessageStream.MessageStreamFactory
import actors.SocketEndpoint.SocketEndpointFactory

object Supervisor {

  case class NewSocket(name: String)

  case class SocketClosed(closedSocket: ActorRef)

  def props(messageStreamFactory: MessageStreamFactory,
    socketEndpointFactory: SocketEndpointFactory): Props =
    Props(classOf[Supervisor], messageStreamFactory, socketEndpointFactory)

}

/**
 * Supervisor that will route messages between the MessageStream and SocketEndpoints
 * @param messageStreamFactory a factory function usedto create MessageStream actors.
 * @param socketEndpointFactory a factory function used to create SocketEndpint actors.
 */
class Supervisor(messageStreamFactory: MessageStreamFactory,
    socketEndpointFactory: SocketEndpointFactory) extends Actor {

  import Supervisor._

  // override val supervisorStrategy =
  //   OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
  //     case _: MessageStream.StartStreamException => Stop
  //   }

  val log = Logging(context.system, this)

  val messageStream = messageStreamFactory(self, context)

  var sockets = ParSet[ActorRef]()

  def receive: Receive = LoggingReceive {

    case m @ NewSocket(name: String) => {

      val newSocket: ActorRef = socketEndpointFactory(context, self, name)

      newSocket forward m

      sockets = sockets + newSocket

      log.info(s"connected socket: ${newSocket.path}")

      if (sockets.size == 1) {
        log.info("starting message stream")
        messageStream ! MessageStream.StartStream
      }
    }

    case SocketClosed(closedSocket: ActorRef) => {

      sockets = sockets - closedSocket

      log.info(s"closed socket: ${closedSocket.path}")

      if (sockets.size == 0) {
        log.info("no connected sockets, shutting down message stream")
        messageStream ! MessageStream.StopStream
      }
    }

    case m @ SocketEndpoint.NewMessage(message: MessageStream.Message) => {
      sockets foreach (_ forward m)
    }

  }
}
