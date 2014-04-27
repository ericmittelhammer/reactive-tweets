package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Akka
import play.api.Play.current
import play.api.libs.json.{ JsValue, JsString }
import play.api.libs.iteratee.{ Iteratee, Enumerator }
import play.api.libs.oauth.{ ConsumerKey, RequestToken }

import akka.actor.{ Actor, Props, ActorSystem, ActorRef, ActorRefFactory }
import akka.pattern.ask
import akka.util.Timeout

import actors.{ Supervisor, SocketEndpoint, MessageStream, TwitterMessageStream }
import actors.OfflineMessageStream

import scala.concurrent.duration._

import global.Global

object Application extends Controller {

  // initiolization stuff...

  val conf = current.configuration

  val actorSystem = ActorSystem("reactive")

  val streamType = conf.getString("messageStream.type", Some(Set("twitter", "offline")))

  val testMessages = List(
    MessageStream.Message(timestamp = new java.util.Date(), author = "Author1", message = "Message1"),
    MessageStream.Message(timestamp = new java.util.Date(), author = "Author2", message = "Message2"),
    MessageStream.Message(timestamp = new java.util.Date(), author = "Author3", message = "Message3")
  )

  val messageStreamFactory = streamType match {
    case Some("offline") => (s: ActorRef, context: ActorRefFactory) => {
      val props = OfflineMessageStream.props(s, testMessages, 500, 1000)
      context.actorOf(props, "OfflineMessageStream")
    }
    case Some("twitter") => (s: ActorRef, context: ActorRefFactory) => {
      (conf.getString("messageStream.url"),
        conf.getString("messageStream.consumerKey"),
        conf.getString("messageStream.consumerSecret"),
        conf.getString("messageStream.accessToken"),
        conf.getString("messageStream.accessTokenSecret")) match {
          case (Some(url: String),
            Some(consumerKey: String),
            Some(consumerSecret: String),
            Some(accessToken: String),
            Some(accessTokenSecret: String)) => {
            val props = TwitterMessageStream.props(s, url, ConsumerKey(consumerKey, consumerSecret), RequestToken(accessToken, accessTokenSecret), 1)
            context.actorOf(props, "TwitterMessageStream")
          }
          case _ => throw new Exception("One or more of the twitter properties is missing")
        }
    }
    // some other messageStream type was specified.
    case _ => throw new Exception("Unknown messageStream type")
  }

  val socketEndpointFactory = (context: ActorRefFactory, s: ActorRef, name: String) =>
    context.actorOf(SocketEndpoint.props(s, name), name)

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