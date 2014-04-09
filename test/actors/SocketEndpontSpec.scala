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

      val msg = MessageStream.Message(timestamp = new java.util.Date(), author = "Author1", message = "Hello")

      // send a message to the socket
      socket ! SocketEndpoint.NewMessage(msg)

      socketActor.channel.end

      // verify the message was processed by the iteratee
      whenReady(f) { result =>
        result should equal(List(SocketEndpoint.messageWrites.writes(msg)))
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
        "payload" -> Json.obj("author" -> "testAuthor", "message" -> "hello")
      )

      whenReady(f) { result =>

        // send a Json message to the iterator
        Enumerator[JsValue](msg).run(result._1)

        // validate that it was parsed and then sent as a message
        // we have to use a partial function here because the timestamp will be set by the SocketEndpoint
        expectMsgPF() {
          case m: SocketEndpoint.NewMessage if (m.message.author == "testAuthor" && m.message.message == "hello") => true
        }
      }

    }

  }

}