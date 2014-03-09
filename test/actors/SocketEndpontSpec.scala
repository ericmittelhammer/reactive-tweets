package actors

import org.scalatest.{ Matchers, BeforeAndAfterAll, WordSpecLike }
import org.scalatest.concurrent.ScalaFutures

import com.typesafe.config.ConfigFactory

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.testkit.{ TestKit, TestProbe, TestActorRef, TestActor, ImplicitSender, DefaultTimeout }
import akka.pattern.ask

import scala.concurrent.duration._
import scala.language.postfixOps

import play.api.libs.json.{ Json, JsValue, JsString }
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

      val socket = TestActorRef(SocketEndpoint.props(supervisor = testActor))
      val socketActor: SocketEndpoint = socket.underlyingActor

      // set the socketEndpoint into its ready state since we aren't
      // explicitly initializing it
      socketActor.context.become(socketActor.connected)

      // create an iteratee to get the input out of the enumerator
      val i = Iteratee.fold(List[JsValue]()) { (list, nextValue: JsValue) =>
        list :+ nextValue
      }

      val f = socketActor.out.run(i)

      // send a message to the socket
      socket ! SocketEndpoint.NewMessage(JsString("hello"))

      socketActor.channel.end

      // verify the message was processed by the iteratee
      whenReady(f) { result =>
        result should equal(List(JsString("hello")))
      }

    }

    "send a message to its supervisor saying it has closed" in {

      val socket = TestActorRef(SocketEndpoint.props(supervisor = testActor))
      val socketActor: SocketEndpoint = socket.underlyingActor

      // get an iteratee and enumerator from the socketEndpoint
      val f = (socket ? Supervisor.NewSocket()).mapTo[(Iteratee[JsValue, Unit], Enumerator[Any])]

      whenReady(f) { result =>

        // pass an EOF message through the returned iteratee,
        // simulating a disconnected websocket
        Enumerator.eof.run(result._1)

        expectMsg(Supervisor.SocketClosed(socket))
      }

    }

    "pass a message on to the supervisor " in {

      val socket = TestActorRef(SocketEndpoint.props(supervisor = testActor))
      val socketActor: SocketEndpoint = socket.underlyingActor

      // get an iteratee and enumerator from the socketEndpoint
      val f = (socket ? Supervisor.NewSocket()).mapTo[(Iteratee[JsValue, Unit], Enumerator[Any])]

      val msg = Json.obj(
        "messageType" -> "newMessage",
        "payload" -> "here's the payload"
      )

      whenReady(f) { result =>

        // send a Json message to the iterator
        Enumerator[JsValue](msg).run(result._1)

        expectMsg(SocketEndpoint.NewMessage(JsString("here's the payload")))
      }

    }

  }

}