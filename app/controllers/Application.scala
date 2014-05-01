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

import actors.{ Supervisor, SocketEndpoint, MessageStream, TwitterMessageStream }
import actors.OfflineMessageStream

import scala.concurrent.duration._

import global.Global

object Application extends Controller {

  // initiolization stuff...

  val actorSystem = ActorSystem("reactive")

  val socketEndpointFactory = (context: ActorRefFactory, s: ActorRef, name: String) =>
    context.actorOf(SocketEndpoint.props(s, name), name)

  val messageStreamFactory = Global.MessageStreamFactory

  val supervisor = actorSystem.actorOf(Supervisor.props(messageStreamFactory, socketEndpointFactory), "supervisor")

  // routes

  def index = Action {
    Ok(views.html.index("Your app is ready"))
  }

  def socket = WebSocket.async[JsValue] { request =>

    implicit val timeout = Timeout(Duration(1, SECONDS))
    (supervisor ? Supervisor.NewSocket(request.queryString("name")(0))).mapTo[(Iteratee[JsValue, Unit], Enumerator[JsValue])]

  }

}