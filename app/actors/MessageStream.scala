package actors

import play.api.libs.ws.WS
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._

import akka.actor.{ Props, ActorRef, Actor, ActorRefFactory }
import akka.event.{ EventStream, Logging, LoggingReceive }

import scala.concurrent.duration._
import scala.language.postfixOps

trait MessageStream

object MessageStream {

  type Message = String

  /**
   * Creates a MessageStream.
   * ActorRef: reference to the supervisor actor
   * ActorRefFactory: the resulting parent context of the retured Actor
   * @return a MessageStream ActorRef
   */
  type MessageStreamFactory = (ActorRef, ActorRefFactory) => ActorRef

  case class StopStream()

  case class StartStream()

  //def props(messages: List[MessageStream.Message]) = Props(classOf[MessageStream], messages)

}

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
    case StartStream() => {
      log.info("Stream Started")
      context.become(started)
      self ! SocketEndpoint.NewMessage(i.next) //send the first message to myself
    }
    case StopStream() => log.warning("Stream already stopped")
    case SocketEndpoint.NewMessage(message: Message) =>
      log.warning("Trying to brodcast to a stopped stream")
  }

  def started: Receive = LoggingReceive {
    case StartStream() => log.warning("Stream already started")
    case StopStream => context.become(stopped)
    case SocketEndpoint.NewMessage(message: Message) => {
      // reset the iterator if we've reached the end of the list
      if (!i.hasNext) i = messageList.iterator
      supervisor ! SocketEndpoint.NewMessage(message) //send the message to the supervisor
      val nextMessageAt =
        scala.util.Random.nextInt(
          (maxMilliseconds - minMilliseconds) + 1) + minMilliseconds
      context.system.scheduler.scheduleOnce(nextMessageAt milliseconds) {
        //schedule the next message to be sent 
        self ! SocketEndpoint.NewMessage(i.next)
      }
    }
  }

  override def receive = stopped

}