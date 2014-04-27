package actors

import play.api.libs.ws.{ WS, Response }
import play.api.libs.oauth.{ OAuthCalculator, ConsumerKey, RequestToken }
import play.api.libs.json.{ Json, JsObject, JsValue, JsString, JsPath, JsResult, JsSuccess, JsError, Reads }
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._

import akka.actor.{ Props, ActorRef, Actor, ActorRefFactory }
import akka.event.{ EventStream, Logging, LoggingReceive }

import scala.concurrent.duration._
import scala.collection.mutable.ArrayBuilder
import scala.util.Random

import java.util.Date

object TwitterMessageStream {

  case class TweetChunk(chunk: Array[Byte])

  def props(
    supervisor: ActorRef,
    url: String,
    consumerKey: ConsumerKey,
    requestToken: RequestToken,
    messageThrottle: Int): Props =
    Props(
      classOf[TwitterMessageStream],
      supervisor,
      url,
      consumerKey,
      requestToken,
      messageThrottle)

  // used to convert a json tweet into the message type
  val tweetReads: Reads[MessageStream.Message] = (
    (JsPath \ "created_at").read[Date](Reads.dateReads("EEE MMM dd HH:mm:ss Z yyyy")) and
    (JsPath \ "user" \ "screen_name").read[String] and
    (JsPath \ "text").read[String]
  )(MessageStream.Message.apply _)

  def tweetToMessage(tweet: JsValue): MessageStream.Message =
    tweet.validate[MessageStream.Message](tweetReads) match {
      case s: JsSuccess[MessageStream.Message] => s.get
      case e: JsError => MessageStream.Message(new Date(), "error", e.toString)
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
 * @param messageThrottle an Integer that represents the number of tweets in the
 *  stream, as a percent, to actually broadcast.  If your api request is returning
 *  a lot of data, this will prevent a crapflood to the clients.
 */
class TwitterMessageStream(
    supervisor: ActorRef,
    url: String,
    consumerKey: ConsumerKey,
    requestToken: RequestToken,
    messageThrottle: Int) extends Actor with MessageStream {

  import MessageStream._

  val log = Logging(context.system, this)

  log.debug(s"consumerKey: ${consumerKey}")
  log.debug(s"requestToken: ${requestToken}")

  val req = WS.url(url).withRequestTimeout(-1).sign(OAuthCalculator(consumerKey, requestToken))

  // a builder to accumulate the bytes as they are received in chunks
  // mutable, but will only be mutated in the Receive function
  val tweetBuilder = new ArrayBuilder.ofByte()

  def stopped: Receive = {

    case StartStream => {
      log.info("TwitterStream Started")
      req.get(headers => if (headers.status != 200) {
        log.warning(s"received a non-ok status code: ${headers.status}")
        Iteratee.getChunks[Array[Byte]] map { (listOfAllChunks: List[Array[Byte]]) =>
          //turn the list of chunks into one long ByteArray
          val body: Array[Byte] = listOfAllChunks.foldLeft(Array[Byte]())((acc: Array[Byte], e: Array[Byte]) => { acc ++ e })
          log.warning(new String(body, "UTF-8"))
        }
      } else {
        // iteratee that is used to turn the raw json from the streaming API
        // into individual messages
        Iteratee.foreach[Array[Byte]] { chunk =>
          self ! TwitterMessageStream.TweetChunk(chunk)
        }
      })
      context.become(started)
    }

    case StopStream => log.warning("Stream already stopped")
  }

  def started: Receive = {
    case StartStream => log.warning("Stream already started")
    case StopStream => context.become(stopped)
    case TwitterMessageStream.TweetChunk(chunk: Array[Byte]) => {
      chunk foreach ((b: Byte) =>
        b match {
          case 10 => { //if we've reached the newline character, parse the buffer & send it.
            val tweet = Json.parse(new String(tweetBuilder.result(), "UTF-8"))
            if (Random.nextInt(100) < messageThrottle) {
              supervisor ! SocketEndpoint.NewMessage(TwitterMessageStream.tweetToMessage(tweet))
            }
            tweetBuilder.clear()
          }
          case _ => tweetBuilder += b
        })
    }
  }

  override def receive: Receive = stopped

}
