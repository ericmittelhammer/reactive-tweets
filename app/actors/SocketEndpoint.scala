package actors

import play.api.libs.ws.WS
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._

import akka.actor.{ Props, ActorRef, Actor }
import akka.event.EventStream

object SocketEndpoint {

  case class NewMessage(message: MessageStream.Message)

  def props(out: Enumerator[MessageStream.Message]) = Props(classOf[SocketEndpoint], out)

}

class SocketEndpoint(out: Enumerator[MessageStream.Message]) extends Actor {

  import SocketEndpoint._

  def receive = {

    case NewMessage(message: MessageStream.Message) => out >>> Enumerator(message)

  }
}