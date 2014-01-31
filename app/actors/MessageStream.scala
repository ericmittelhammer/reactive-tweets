package actors

import play.api.libs.ws.WS
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._

import akka.actor.{ Props, ActorRef, Actor }
import akka.event.EventStream

object MessageStream {

  type Message = String

  case class Subscribe(actor: ActorRef)

  case class Unsubscribe(actor: ActorRef)

  case class Broadcast(message: Message)

}

class MessageStream(messages: List[MessageStream.Message]) extends Actor {

  import MessageStream._

  val eventStream = new EventStream

  def receive = {

    case Subscribe(actor: ActorRef) => eventStream.subscribe(sender, classOf[SocketEndpoint.NewMessage])

    case Unsubscribe(actor: ActorRef) => eventStream.unsubscribe(sender)

    case Broadcast(message: Message) => eventStream.publish(SocketEndpoint.NewMessage(message))
  }
}