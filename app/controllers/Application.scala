package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Akka
import play.api.Play.current
import play.api.libs.json.{ JsValue, JsString }
import play.api.libs.iteratee.{ Iteratee, Enumerator }

import akka.actor.{ Actor, Props, ActorSystem, ActorRef, ActorRefFactory }
import akka.pattern.ask
import akka.util.Timeout

import actors.{ Supervisor, SocketEndpoint, MessageStream }
import actors.OfflineMessageStream

import scala.concurrent.duration._

import global.Global

object Application extends Controller {

  var socketCount = 0

  val actorSystem = ActorSystem("reactive")

  val testMessages = List(
    MessageStream.Message(timestamp = new java.util.Date(), author = "Author1", message = "Message1"),
    MessageStream.Message(timestamp = new java.util.Date(), author = "Author2", message = "Message2"),
    MessageStream.Message(timestamp = new java.util.Date(), author = "Author3", message = "Message3")
  )

  val messageStreamFactory = (s: ActorRef, context: ActorRefFactory) => {
    val props = OfflineMessageStream.props(s, testMessages, 500, 1000)
    context.actorOf(props, "OfflineMessageStream")
  }

  val noOpMessageStreamFactory = (s: ActorRef, context: ActorRefFactory) => {

    context.actorOf(Props(new Actor {
      def receive = {
        case _ => Unit
      }
    }))

  }

  val socketEndpointFactory = (s: ActorRef, context: ActorRefFactory, name: Option[String]) => {
    val props = SocketEndpoint.props(s)
    name match {
      case Some(n) => context.actorOf(props, n)
      case _ => context.actorOf(props)
    }
  }

  val supervisor = actorSystem.actorOf(Supervisor.props(messageStreamFactory, socketEndpointFactory), "supervisor")

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def socket = WebSocket.async[JsValue] { request =>

    implicit val timeout = Timeout(Duration(1, SECONDS))
    socketCount = socketCount + 1
    (supervisor ? Supervisor.NewSocket(Some(s"testSocket${socketCount}"))).mapTo[(Iteratee[JsValue, Unit], Enumerator[JsValue])]

  }

}