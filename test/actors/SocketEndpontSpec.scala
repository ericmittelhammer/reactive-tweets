package actors

import org.scalatest.{ Matchers, BeforeAndAfterAll, WordSpecLike }
import org.scalatest.concurrent.ScalaFutures

import com.typesafe.config.ConfigFactory

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.testkit.{ TestKit, TestProbe, TestActorRef, TestActor, ImplicitSender, DefaultTimeout }

import scala.concurrent.duration._
import scala.language.postfixOps

import play.api.libs.iteratee.{ Iteratee, Enumerator, Input, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._

object SocketEndpointSpec {
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

class SocketEndpointSpec extends TestKit(ActorSystem("SocketEndpointSystem",
  ConfigFactory.parseString(SocketEndpointSpec.config)))
    with DefaultTimeout with ImplicitSender with ScalaFutures
    with WordSpecLike with Matchers with BeforeAndAfterAll {
  import SocketEndpointSpec._

  override def afterAll {
    shutdown(system)
  }

  "a SocketEndpoint" should {
    "add a message to its enumerator" in {
      val testOut: Enumerator[MessageStream.Message] = Enumerator.enumInput(Input.Empty)
      val socket = TestActorRef(SocketEndpoint.props(supervisor = testActor))
      val socketActor: SocketEndpoint = socket.underlyingActor
      socket ! SocketEndpoint.NewMessage("hello")
      val i = Iteratee.fold(List[String]()) { (list, nextString: String) =>
        println(s"appending ${nextString}")
        list :+ nextString
      }

      val f = socketActor.out.run(i)

      whenReady(f) { result =>
        result should equal(List("hello"))
      }
      //messageStexpectMsg(MessageStream.StartStream())
    }

  }

}