package actors

import play.api.libs.ws.WS
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._

import akka.actor.{ Props, ActorRef, Actor }
import akka.event.{ EventStream, Logging, LoggingReceive }

import scala.concurrent.duration._

object MessageStream {

  type Message = String

  case class Subscribe(actor: ActorRef)

  case class Unsubscribe(actor: ActorRef)

  case class Broadcast(message: Message)

  case class StopStream()

  case class StartStream()

  //def props(messages: List[MessageStream.Message]) = Props(classOf[MessageStream], messages)

}

trait MessageStream {

  import MessageStream._

  val eventStream = new EventStream

  def commonReceive: Actor.Receive = {

    case Subscribe(actor: ActorRef) => eventStream.subscribe(actor, classOf[SocketEndpoint.NewMessage])

    case Unsubscribe(actor: ActorRef) => eventStream.unsubscribe(actor)

    //case Broadcast(message: Message) => eventStream.publish(SocketEndpoint.NewMessage(message))
  }
}

class OfflineMessageStream(messageList: List[MessageStream.Message]) extends Actor with MessageStream {

  import MessageStream._

  val log = Logging(context.system, this)

  //def messages: Stream[MessageStream.Message] = messageList.toStream append messages

  var i = messageList.iterator

  def stopped: Receive = LoggingReceive {
    commonReceive orElse {
      case StartStream() => {
        log.info("Stream Started")
        context.become(started)
        self ! Broadcast(i.next) //send the first message to myself
      }
      case StopStream() => log.warning("Stream already stopped")
      case Broadcast(message: Message) => log.warning("Trying to brodcast to a stopped stream")
    }
  }

  def started: Receive = LoggingReceive {
    commonReceive orElse {
      case StartStream() => log.warning("Stream already started")
      case StopStream => context.become(stopped)
      case Broadcast(message: Message) => {
        if (!i.hasNext) i = messageList.iterator // reset the iterator if we've reached the end of the list
        eventStream.publish(SocketEndpoint.NewMessage(message)) //publish the message to the queue
        val nextMessageAt = scala.util.Random.nextInt(2500) + 50
        context.system.scheduler.scheduleOnce(nextMessageAt milliseconds) { //schedule the next message to be sent
          self ! Broadcast(i.next)
        }
      }
    }
  }

  override def receive = stopped

}