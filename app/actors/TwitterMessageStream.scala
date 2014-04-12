package actors

import play.api.libs.ws.WS
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.json.{ Json, JsValue, JsObject }
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._

import akka.actor.{ Props, ActorRef, Actor, ActorRefFactory }
import akka.event.{ EventStream, Logging, LoggingReceive }

import scala.concurrent.duration._

object TwitterMessageStream {

  def tweetToMessage(tweet: JsObject): MessageStream.Message = {

  }

}

/**
 * Actor that will connect to the Twitter stream and handle incoming messages;
 * parsing and tranforming them before sending them to the supervisor for
 * broadcasting.
 * @prarm supervisor reference to the supervisor
 * @param url url of the twitter stream to connect to
 * @param consumerKey used for Twitter OAuth
 * @param accessToken used for Twitter OAuth
 */
class TwitterMessageStream(
    supervisor: ActorRef,
    url: String,
    consumerKey: String,
    accessToken: String) extends Actor with MessageStream {

  val req = WS.url(url).withRequestTimeout(-1).sign(OAuthCalculator(consumerKey, accessToken))

  // iteratee that is used to turn the raw json from the streaming API
  // into individual messages
  val iteratee = Iteratee.foreach[Array[Byte]] { chunk =>

    val chunkedString = new String(chunk, "UTF-8")

    val json = Json.parse(chunkedString)

    json.asOpt[String].map { tweet =>
      supervisor ! SocketEndpoint.NewMessage(tweetToMessage(tweet))
    }
  }

  val log = Logging(context.system, this)

  def stopped: Receive = {

    case StartStream => {
      log.info("TwitterStream Started")
      context.become(started)
    }

    case StopStream => log.warning("Stream already stopped")
  }

  def started: Receive = {
    case StartStream => log.warning("Stream already started")
    case StopStream => context.become(stopped)
  }

  override def receive: Receive = stopped

}
