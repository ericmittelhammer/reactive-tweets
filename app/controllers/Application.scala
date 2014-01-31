package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Akka
import play.api.Play.current

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorSystem

import actors.Supervisor

object Application extends Controller {

  val sockets = Akka.system.actorOf(Props(classOf[Supervisor]))

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

}