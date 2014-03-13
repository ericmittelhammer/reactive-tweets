package actors

import akka.actor.{ Props, ActorRef, Actor, ActorRefFactory }
import akka.event.{ EventStream, Logging, LoggingReceive }

import scala.concurrent.duration._
import scala.language.postfixOps

import play.api.libs.concurrent.Execution.Implicits._

object OfflineMessageStream {

  def props(
    supervisor: ActorRef,
    messageList: List[MessageStream.Message],
    minMilliseconds: Int,
    maxMilliseconds: Int) =
    Props(
      classOf[OfflineMessageStream],
      supervisor,
      messageList,
      minMilliseconds,
      maxMilliseconds)

  case object NextMessage

}

/**
 * A message stream that is provided messages to stream.
 * Wlll generate messages at a random interval between minMilliseconds and maxMilliseconds
 * @constructor create a new OfflineMessageStream
 * @param supervisor the superivsing actor that should receive the messages
 * @param messageList the predifned list of messages to be streamed
 * @param minMilliseconds lower bound for time between messages
 * @param maxMilliseconds upper bound for time between messages
 */

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
    case StartStream => {
      log.info("Stream Started")
      context.become(started)
      self ! OfflineMessageStream.NextMessage //send the first message to myself
    }
    case StopStream => log.warning("Stream already stopped")
    case SocketEndpoint.NewMessage(message: Message) =>
      log.warning("Stream already stopped")
  }

  def started: Receive = LoggingReceive {
    case StartStream => log.warning("Stream already started")
    case StopStream => context.become(stopped)
    case OfflineMessageStream.NextMessage => {
      // reset the iterator if we've reached the end of the list
      if (!i.hasNext) i = messageList.iterator
      val nextMsg = i.next
      supervisor ! SocketEndpoint.NewMessage(nextMsg) //send the message to the supervisor
      val nextMessageAt =
        scala.util.Random.nextInt(
          (maxMilliseconds - minMilliseconds) + 1) + minMilliseconds
      context.system.scheduler.scheduleOnce(nextMessageAt milliseconds) {
        //schedule the next message to be sent 
        self ! OfflineMessageStream.NextMessage
      }
    }
  }

  override def receive = stopped

}