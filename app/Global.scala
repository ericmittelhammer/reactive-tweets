package global

import play.api._
import play.api.Play.current
import scala.io.Source
import play.api.libs.concurrent.Akka
import akka.actor.{ Props, ActorRef }

import actors.MessageStream

object Global extends GlobalSettings {

  //lazy val messageStream: ActorRef = createStream()

  override def onStart(app: Application) {
    Logger.info("Application has started")

  }

  override def onStop(app: Application) {
    Logger.info("Application shutdown...")
  }

  // def createStream(): ActorRef = {

  //   val messages = Play.getFile("conf/data/data.txt")

  //   val messageList = for (line <- Source.fromFile(messages).getLines()) yield (line)

  //   Logger.info(s"loaded ${messageList.size} messages")

  //   Akka.system.actorOf(MessageStream.props(messageList.toList))

  // }
}
