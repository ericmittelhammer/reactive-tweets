package actors

import play.api.libs.ws.WS
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.json.{ Json, JsValue, JsObject }
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._

import akka.actor.{ Props, ActorRef, Actor, ActorRefFactory }
import akka.event.{ EventStream, Logging, LoggingReceive }

import scala.concurrent.duration._

/*object TwitterMessageStream {

  def tweetToMessage(tweet: JsObject): JsObject = {

  }

}

class TwitterMessageStream(
    supervisor: ActorRef,
    url: String,
    consumerKey: String,
    accessToken: String) extends Actor with MessageStream {

  val req = WS.url(url).withRequestTimeout(-1).sign(OAuthCalculator(consumerKey, accessToken))

  // iteratee that is used to turn the streaming API
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

  override def receive = stopped

}*/
