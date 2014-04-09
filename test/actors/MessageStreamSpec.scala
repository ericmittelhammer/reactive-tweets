package actors

import org.scalatest.{ Matchers, BeforeAndAfterAll, WordSpecLike }

import com.typesafe.config.ConfigFactory

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.testkit.{ TestKit, ImplicitSender, DefaultTimeout }

import play.api.libs.json.JsString

import scala.concurrent.duration._
import scala.language.postfixOps

object OfflineMessageStreamSpec {
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

class OfflineMessageStreamSpec extends TestKit(ActorSystem("OMSSystem",
  ConfigFactory.parseString(OfflineMessageStreamSpec.config)))
    with DefaultTimeout with ImplicitSender
    with WordSpecLike with Matchers with BeforeAndAfterAll {
  import OfflineMessageStreamSpec._

  override def afterAll {
    shutdown(system)
  }

  val testMessages = List(
    MessageStream.Message(timestamp = new java.util.Date(), author = "Author1", message = "Message1"),
    MessageStream.Message(timestamp = new java.util.Date(), author = "Author2", message = "Message2"),
    MessageStream.Message(timestamp = new java.util.Date(), author = "Author3", message = "Message3")
  )

  val oms = system.actorOf(OfflineMessageStream.props(self, testMessages, 50, 3000), "oms")

  "an unstarted OfflineMessageStream Actor" should {
    "not publish any messages" in {
      //oms ! MessageStream.Subscribe(testActor)
      expectNoMsg
    }
  }

  "a started OfflineMessageStream Actor" should {
    "publish a message within 3 seconds" in {
      oms ! MessageStream.StartStream
      expectMsgAllClassOf(3000 milliseconds, classOf[SocketEndpoint.NewMessage])
    }
  }

  "a running OfflineMessageStream Actor" should {
    "keep publishing after it has exhausted list of messages" in {
      receiveN(4, 12000 milliseconds)
    }
  }

  "a running OfflineMessageStream Actor" should {
    "stop publishing after it is told to sut down" in {
      oms ! MessageStream.StopStream
      expectNoMsg
    }
  }

}