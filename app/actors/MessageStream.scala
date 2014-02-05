package actors

import play.api.libs.ws.WS
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._

import akka.actor.{ Props, ActorRef, Actor }
import akka.event.{ EventStream, Logging, LoggingReceive }

import scala.concurrent.duration._
import scala.language.postfixOps

trait MessageStream

object MessageStream {

  type Message = String

  case class NewMessage(message: Message)

  case class StopStream()

  case class StartStream()

  //def props(messages: List[MessageStream.Message]) = Props(classOf[MessageStream], messages)

}

object OfflineMessageStream {

  def props(
    supervisor: ActorRef,
    messages: List[MessageStream.Message],
    minMilliseconds: Int,
    maxMilliseconds: Int) =
    Props(
      classOf[OfflineMessageStream],
      supervisor,
      messages,
      minMilliseconds,
      maxMilliseconds)

}

class OfflineMessageStream(
    supervisor: ActorRef,
    messageList: List[MessageStream.Message],
    minMilliseconds: Int,
    maxMilliseconds: Int) extends Actor with MessageStream {

  import MessageStream._

  val log = Logging(context.system, this)

  //def messages: Stream[MessageStream.Message] = messageList.toStream append messages

  var i = messageList.iterator

  def stopped: Receive = LoggingReceive {
    case StartStream() => {
      log.info("Stream Started")
      context.become(started)
      self ! NewMessage(i.next) //send the first message to myself
    }
    case StopStream() => log.warning("Stream already stopped")
    case NewMessage(message: Message) =>
      log.warning("Trying to brodcast to a stopped stream")
  }

  def started: Receive = LoggingReceive {
    case StartStream() => log.warning("Stream already started")
    case StopStream => context.become(stopped)
    case NewMessage(message: Message) => {
      // reset the iterator if we've reached the end of the list
      if (!i.hasNext) i = messageList.iterator
      supervisor ! NewMessage(message) //send the message to the supervisor
      val nextMessageAt =
        scala.util.Random.nextInt(
          (maxMilliseconds - minMilliseconds) + 1) + minMilliseconds
      context.system.scheduler.scheduleOnce(nextMessageAt milliseconds) {
        //schedule the next message to be sent 
        self ! NewMessage(i.next)
      }
    }
  }

  override def receive = stopped

}