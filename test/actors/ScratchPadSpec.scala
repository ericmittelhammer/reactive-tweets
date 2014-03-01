package actors

import org.scalatest.{ Matchers, BeforeAndAfterAll, WordSpecLike }
import org.scalatest.concurrent.ScalaFutures

import com.typesafe.config.ConfigFactory

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.testkit.{ TestKit, TestProbe, ImplicitSender, DefaultTimeout }
import akka.event.{ Logging, LoggingReceive }
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.duration._
import scala.language.postfixOps

import play.api.libs.iteratee.{ Iteratee, Enumerator, Input, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._

object ScratchPadSpec {
  val config = """
        akka {
            loglevel = "DEBUG"
            actor{
                debug{
                    receive = on
                }
            }
        }"""
}

case class Message(s: String)

class Receiver extends Actor {
  val log = Logging(context.system, this)
  def receive = LoggingReceive {
    case Message(s: String) => {
      log.debug(s"sender is: ${sender.path}")
      sender ! Message(s"You said: ${s}")
    }
  }
}

class Forwarder extends Actor {
  def receive = LoggingReceive {
    case m @ Message(s: String) => {
      val r = context.system.actorOf(Props(classOf[Receiver]), "receiver")
      r forward m
    }
  }
}

class ScratchPadSpec extends TestKit(ActorSystem("ScratchPadSystem",
  ConfigFactory.parseString(ScratchPadSpec.config)))
    with DefaultTimeout with ImplicitSender with ScalaFutures
    with WordSpecLike with Matchers with BeforeAndAfterAll {
  import SocketEndpointSpec._

  override def afterAll {
    shutdown(system)
  }

  "an asked message" should {
    "still reply when forwarded" in {

      var forwarder = system.actorOf(Props(classOf[Receiver]), "forwarder")

      implicit val timeout = Timeout(Duration(1, SECONDS))

      val f = (forwarder ? Message("foo")).mapTo[Message]

      whenReady(f) { result =>
        result should equal(Message("You said: foo"))
      }

    }

  }

}