package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Akka
import play.api.Play.current

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorSystem

import actors.Supervisor

import global.Global

object Application extends Controller {

  //val messageStream = Global.messageStream

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

}