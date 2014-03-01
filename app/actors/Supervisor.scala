package actors

import play.api.libs.ws.WS
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current

import akka.actor.{ Props, ActorRef, Actor, ActorRefFactory }
import akka.event.{ EventStream, Logging, LoggingReceive }

import scala.collection.parallel.ParSet

object Supervisor {

  case class NewSocket()

  case class SocketClosed(closedSocket: ActorRef)

  def props(messageStream: ActorRef,
    socketEndpointFactory: ActorRefFactory => ActorRef) =
    Props(classOf[Supervisor], messageStream, socketEndpointFactory)

}

class Supervisor(messageStream: ActorRef,
    socketEndpointFactory: ActorRefFactory => ActorRef) extends Actor {

  import Supervisor._

  val log = Logging(context.system, this)

  var sockets = ParSet[ActorRef]()

  def receive = LoggingReceive {

    case m @ NewSocket() => {
      val newSocket: ActorRef = socketEndpointFactory(context.system)
      newSocket forward m
      sockets = sockets + newSocket
      if (sockets.size == 1) messageStream ! MessageStream.StartStream()
    }

    case SocketClosed(closedSocket: ActorRef) => {
      sockets = sockets - closedSocket
      //messageStream ! MessageStream.Unsubscribe(closedSocket)
      if (sockets.size == 0) messageStream ! MessageStream.StopStream()
    }

    case m @ MessageStream.NewMessage(message: MessageStream.Message) => {
      sockets foreach (_ forward m)
    }

  }
}