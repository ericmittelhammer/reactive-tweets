package actors

import play.api.libs.ws.WS
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current

import akka.actor.{ Props, ActorRef, Actor }
import akka.event.EventStream

object Supervisor {

  case class NewSocket(out: Enumerator[MessageStream.Message])

}

class Supervisor extends Actor {

  import Supervisor._

  var sockets = Set[ActorRef]()

  def receive = {

    case NewSocket(out: Enumerator[MessageStream.Message]) => {
      val newSocket = context.actorOf(SocketEndpoint.props(out))

      sockets = sockets + newSocket
    }

  }
}