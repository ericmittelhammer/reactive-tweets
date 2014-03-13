package actors

import akka.actor.{ ActorRef, ActorRefFactory }
import play.api.libs.json.JsValue

trait MessageStream

object MessageStream {

  type Message = JsValue

  /**
   * Creates a MessageStream.
   * ActorRef: reference to the supervisor actor
   * ActorRefFactory: the resulting parent context of the retured Actor
   * @return a MessageStream ActorRef
   */
  type MessageStreamFactory = (ActorRef, ActorRefFactory) => ActorRef

  case object StopStream

  case object StartStream

  //def props(messages: List[MessageStream.Message]) = Props(classOf[MessageStream], messages)

}

