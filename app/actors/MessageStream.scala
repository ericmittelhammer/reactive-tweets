package actors

import scala.collection.parallel.ParSet

import play.api.libs.ws.WS
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._

import akka.actor.{Props, ActorRef, Actor}

object MessageStream {

    case class Subscribe(actor: ActorRef)

    case class Unsubscribe(actor: ActorRef)
    
}

class MessageStream extends Actor {

    import MessageStream._

    type Message = String

    var subscribers = ParSet[ActorRef]()

    val messages = Enumerator[Message]("The discarded bicycle pressures the rod.", "How does the definitive plaster honor an appointed scholar?", "A hollow parade tends a worthwhile deaf. Why can't the circle migrate?")
    
    val (streamEnumerator, streamChannel) = Concurrent.broadcast[Message]

    streamEnumerator |>>> Iteratee.foreach[Message](m => subscribers.foreach(_ ! m))

    def receive = {
        
        case  Subscribe(actor: ActorRef) => subscribers = subscribers + actor

        case  Unsubscribe(actor: ActorRef) => subscribers = subscribers - actor
    }
}