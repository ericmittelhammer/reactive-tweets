package global

import play.api._
import play.api.Play.current
import scala.io.Source
import play.api.libs.concurrent.Akka
import play.api.libs.oauth.{ ConsumerKey, RequestToken }
import akka.actor.{ Actor, Props, ActorSystem, ActorRef, ActorRefFactory }

import actors.{ MessageStream, TwitterMessageStream, OfflineMessageStream }

object Global extends GlobalSettings {

  lazy val MessageStreamFactory = getMessageStreamFactory(current.configuration)

  override def onStart(app: Application) {
    Logger.info("Application has started")
    //force initialization
    MessageStreamFactory
  }

  override def onStop(app: Application) {
    Logger.info("Application shutdown...")
  }

  def getMessageStreamFactory(conf: Configuration): MessageStream.MessageStreamFactory = {

    val streamType = conf.getString("messageStream.type", Some(Set("twitter", "offline")))

    streamType match {

      case Some("offline") => ((s: ActorRef, context: ActorRefFactory) => {
        val props = OfflineMessageStream.props(s, testMessages, 500, 1000)
        context.actorOf(props, "OfflineMessageStream")
      })

      case Some("twitter") => (for (
        url <- conf.getString("messageStream.url");
        consumerKey <- conf.getString("messageStream.consumerKey");
        consumerSecret <- conf.getString("messageStream.consumerSecret");
        accessToken <- conf.getString("messageStream.accessToken");
        accessTokenSecret <- conf.getString("messageStream.accessTokenSecret")
      ) yield (s: ActorRef, context: ActorRefFactory) => {
        val props = TwitterMessageStream.props(s, url, ConsumerKey(consumerKey, consumerSecret), RequestToken(accessToken, accessTokenSecret), 1)
        context.actorOf(props, "TwitterMessageStream")
      }).getOrElse(throw new UnexpectedException(Some("Some Twitter configuration params are missing")))

      // some other messageStream type was specified.
      case _ => throw new Exception("Unknown messageStream type")

    }
  }

  val testMessages = List(
    MessageStream.Message(timestamp = new java.util.Date(), author = "Author1", message = "Message1"),
    MessageStream.Message(timestamp = new java.util.Date(), author = "Author2", message = "Message2"),
    MessageStream.Message(timestamp = new java.util.Date(), author = "Author3", message = "Message3")
  )

}
