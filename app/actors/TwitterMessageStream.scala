package actors

import play.api.libs.ws.WS
import play.api.libs.json.JsValue
import play.api.libs.iteratee.{ Iteratee, Enumerator, Concurrent }
import play.api.libs.concurrent.Execution.Implicits._

import akka.actor.{ Props, ActorRef, Actor, ActorRefFactory }
import akka.event.{ EventStream, Logging, LoggingReceive }

import scala.concurrent.duration._

object TwitterMessageStream