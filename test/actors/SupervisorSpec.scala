package actors

import org.scalatest.{ Matchers, BeforeAndAfterAll, WordSpecLike }

import com.typesafe.config.ConfigFactory

import akka.actor.{ Actor, ActorRef, ActorRefFactory, ActorSystem, Props }
import akka.pattern.{ ask }
import akka.testkit.{ TestKit, TestProbe, ImplicitSender, DefaultTimeout }

import scala.concurrent.duration._
import scala.language.postfixOps

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
      def socketEndpointFactory(a: ActorRefFactory): ActorRef = TestProbe().ref

      val supervisor = system.actorOf(Supervisor.props(messageStream.ref, socketEndpointFactory), "supervisor")

      supervisor ! Supervisor.NewSocket()
      messageStream.expectMsg(MessageStream.StartStream())
    }

    "send each of its subscribed sockets a NewMessage" in {

      val messageStream = TestProbe()
      val socket1 = TestProbe()
      val socket2 = TestProbe()
      val i = List(socket1, socket2).iterator
      def socketEndpointFactory(a: ActorRefFactory): ActorRef = i.next().ref

      val supervisor = system.actorOf(Supervisor.props(messageStream.ref, socketEndpointFactory), "supervisor2")

      supervisor ! Supervisor.NewSocket()
      supervisor ! Supervisor.NewSocket()
      supervisor ! MessageStream.NewMessage("hello")
      socket1.expectMsg(MessageStream.NewMessage("hello"))
      socket2.expectMsg(MessageStream.NewMessage("hello"))
    }

    "send a StopStream message when its final socket has been closed" in {

      val messageStream = TestProbe()
      val socket1 = TestProbe()
      val socket2 = TestProbe()
      val i = List(socket1, socket2).iterator
      def socketEndpointFactory(a: ActorRefFactory): ActorRef = i.next().ref

      val supervisor = system.actorOf(Supervisor.props(messageStream.ref, socketEndpointFactory), "supervisor3")

      supervisor ! Supervisor.NewSocket()
      supervisor ! Supervisor.NewSocket()

      supervisor ! Supervisor.SocketClosed(socket1.ref)
      supervisor ! Supervisor.SocketClosed(socket2.ref)
      messageStream.expectMsg(MessageStream.StopStream())
    }
  }

}