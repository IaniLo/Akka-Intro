package com.hello.actors.Bank

import akka.actor.Actor
import akka.event.LoggingReceive
import akka.actor.actorRef2Scala

class Counter extends Actor {

  var count = 0
  def receive = LoggingReceive {
    case "incr" => count += 1
    case "get" => sender ! count + "a"
  }

  //  def counter(n: Int): Receive = {
  //    case "incr" => context.become(counter(n + 1))
  //    case "get" => sender ! n
  //  }
  //  def receive = counter(0)
}