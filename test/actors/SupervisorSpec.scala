package actors

import org.scalatest.{ Matchers, BeforeAndAfterAll, WordSpecLike }

import com.typesafe.config.ConfigFactory

import akka.actor.{ Actor, ActorRef, ActorRefFactory, ActorSystem, Props }
import akka.pattern.{ ask }
import akka.testkit.{ TestKit, TestActor, TestActorRef, TestProbe, ImplicitSender, DefaultTimeout }

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.collection.parallel.ParSet

import play.api.libs.json.JsString
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._

object SupervisorSpec {
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

class SupervisorSpec extends TestKit(ActorSystem("SupervisorSystem",
  ConfigFactory.parseString(SupervisorSpec.config)))
    with DefaultTimeout with ImplicitSender
    with WordSpecLike with Matchers with BeforeAndAfterAll {
  import SupervisorSpec._

  override def afterAll {
    shutdown(system)
  }

  "a supervisor" should {
    "send the message stream a StartStream message when its first socket is added" in {

      val messageStream = TestProbe()

      def messageStreamFactory(s: ActorRef, a: ActorRefFactory): ActorRef = messageStream.ref

      def socketEndpointFactory(s: ActorRef, a: ActorRefFactory, n: Option[String]): ActorRef = TestProbe().ref

      val supervisor = system.actorOf(Supervisor.props(messageStreamFactory, socketEndpointFactory), "supervisor")

      supervisor ! Supervisor.NewSocket()

      messageStream.expectMsg(MessageStream.StartStream)
    }

    "Forward a NewSocket message to a created SocketEndpoint" in {

      val messageStream = TestProbe()

      def messageStreamFactory(s: ActorRef, a: ActorRefFactory): ActorRef = messageStream.ref

      val socket1 = TestProbe()

      def socketEndpointFactory(s: ActorRef, a: ActorRefFactory, n: Option[String]): ActorRef = socket1.ref

      val supervisor = TestActorRef(Supervisor.props(messageStreamFactory, socketEndpointFactory), "supervisor2")

      supervisor ! Supervisor.NewSocket()

      socket1.expectMsg(Supervisor.NewSocket())
    }

    "send each of its subscribed sockets a NewMessage" in {

      val messageStream = TestProbe()

      def messageStreamFactory(s: ActorRef, a: ActorRefFactory): ActorRef = messageStream.ref

      val socket1 = TestProbe()
      val socket2 = TestProbe()
      val i = List(socket1, socket2).iterator
      def socketEndpointFactory(s: ActorRef, a: ActorRefFactory, n: Option[String]): ActorRef = i.next().ref

      val supervisorRef = TestActorRef(Supervisor.props(messageStreamFactory, socketEndpointFactory), "supervisor3")

      val supervisor: Supervisor = supervisorRef.underlyingActor

      supervisor.sockets = ParSet(socket1.ref, socket2.ref)

      supervisorRef ! SocketEndpoint.NewMessage(JsString("hello"))

      socket1.expectMsg(SocketEndpoint.NewMessage(JsString("hello")))

      socket2.expectMsg(SocketEndpoint.NewMessage(JsString("hello")))
    }

    "send a StopStream message when its final socket has been closed" in {

      val messageStream = TestProbe()

      def messageStreamFactory(s: ActorRef, a: ActorRefFactory): ActorRef = messageStream.ref

      val socket1 = TestProbe()
      val socket2 = TestProbe()
      val i = List(socket1, socket2).iterator
      def socketEndpointFactory(s: ActorRef, a: ActorRefFactory, n: Option[String]): ActorRef = i.next().ref

      val supervisorRef = TestActorRef(Supervisor.props(messageStreamFactory, socketEndpointFactory), "supervisor4")

      val supervisor: Supervisor = supervisorRef.underlyingActor

      supervisor.sockets = ParSet(socket1.ref, socket2.ref)

      supervisorRef ! Supervisor.SocketClosed(socket1.ref)

      messageStream.expectNoMsg

      supervisorRef ! Supervisor.SocketClosed(socket2.ref)

      messageStream.expectMsg(MessageStream.StopStream)
    }
  }

}