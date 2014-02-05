package actors

import play.api.libs.ws.WS
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current

import akka.actor.{ Props, ActorRef, Actor }
import akka.event.{ EventStream, Logging, LoggingReceive }

import scala.collection.parallel.ParSet

object Supervisor {

  case class NewSocket(socket: ActorRef)

  case class SocketClosed(closedSocket: ActorRef)

  def props(messageStream: ActorRef) = Props(classOf[Supervisor], messageStream)

}

class Supervisor(messageStream: ActorRef) extends Actor {

  import Supervisor._

  val log = Logging(context.system, this)

  var sockets = ParSet[ActorRef]()

  def receive = LoggingReceive {

    case NewSocket(newSocket: ActorRef) => {
      if (sockets.size == 0) messageStream ! MessageStream.StartStream()
      sockets = sockets + newSocket
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