package actors

import play.api.libs.ws.WS
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current

import akka.actor.{ Props, ActorRef, Actor }
import akka.event.EventStream

object Supervisor {

  case class NewSocket(in: Iteratee[MessageStream.Message, String], out: Enumerator[MessageStream.Message])

  case class SocketClosed(closedSocket: ActorRef)

  def props(messageStream: ActorRef) = Props(classOf[Supervisor], messageStream)

}

class Supervisor(messageStream: ActorRef) extends Actor {

  import Supervisor._

  var sockets = Set[ActorRef]()

  def receive = {

    case NewSocket(in: Iteratee[MessageStream.Message, String], out: Enumerator[MessageStream.Message]) => {
      val newSocket = context.actorOf(SocketEndpoint.props(in, out))
      if (sockets.size == 0) messageStream ! MessageStream.StartStream()
      sockets = sockets + newSocket
    }

    case SocketClosed(closedSocket: ActorRef) => {
      sockets = sockets - closedSocket
      //messageStream ! MessageStream.Unsubscribe(closedSocket)
      if (sockets.size == 0) messageStream ! MessageStream.StopStream()
    }

  }
}