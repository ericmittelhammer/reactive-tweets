package actors

import org.scalatest.{ Matchers, BeforeAndAfterAll, WordSpecLike }

import com.typesafe.config.ConfigFactory

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
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

  val messageStream = TestProbe()
  val socket1 = TestProbe()
  val socket2 = TestProbe()
  val supervisor = system.actorOf(Supervisor.props(messageStream = messageStream.ref), "supervisor")

  "a supervisor" should {
    "send the message stream a StartStream message when it's first socket is added" in {
      supervisor ! Supervisor.NewSocket(socket1.ref)
      messageStream.expectMsg(MessageStream.StartStream())
    }

    "send each of it's subscribed sockets a NewMessage" in {
      supervisor ! Supervisor.NewSocket(socket2.ref)
      messageStream.expectNoMsg
      supervisor ! MessageStream.NewMessage("hello")
      socket1.expectMsg(MessageStream.NewMessage("hello"))
      socket2.expectMsg(MessageStream.NewMessage("hello"))
    }

    "send a StopStream message when it's final socket has been closed" in {
      supervisor ! Supervisor.SocketClosed(socket1.ref)
      messageStream.expectNoMsg
      supervisor ! Supervisor.SocketClosed(socket2.ref)
      messageStream.expectMsg(MessageStream.StopStream())
    }
  }

}