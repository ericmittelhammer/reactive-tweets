
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike
import org.scalatest.matchers.ShouldMatchers

import com.typesafe.config.ConfigFactory

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.DefaultTimeout
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import scala.concurrent.duration._
import scala.collection.immutable

import actors.OfflineMessageStream
import actors.MessageStream
import actors.SocketEndpoint

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
    with WordSpecLike with ShouldMatchers with BeforeAndAfterAll {
  import OfflineMessageStreamSpec._

  override def afterAll {
    shutdown(system)
  }

  val oms = system.actorOf(OfflineMessageStream.props(self, List("one", "two", "three"), 50, 3000), "oms")

  "an unstarted OfflineMessageStream Actor" should {
    "not publish any messages" in {
      //oms ! MessageStream.Subscribe(testActor)
      expectNoMsg
    }
  }

  "an started OfflineMessageStream Actor" should {
    "publish a message within 3 seconds" in {
      oms ! MessageStream.StartStream()
      expectMsgAllClassOf(3000 milliseconds, classOf[MessageStream.NewMessage])
    }
  }

  "an running OfflineMessageStream Actor" should {
    "keep publishing after it has exhausted list of messages" in {
      receiveN(4, 12000 milliseconds)
    }
  }

}