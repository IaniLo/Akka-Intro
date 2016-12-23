
package com.hello.actors.Bank

import akka.actor.Actor
import akka.actor.Props
import akka.actor.actorRef2Scala


class MainCounter extends Actor {

  val counter = context.actorOf(Props[Counter], "counter123")

  counter ! "incr"
  counter ! "incr"
  counter ! "incr"
  counter ! "get"

  def receive = {
    case count: Int =>
      println(s"count was $count")
      context.stop(self);
  }
}