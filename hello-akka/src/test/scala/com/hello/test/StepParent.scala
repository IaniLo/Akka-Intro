package com.hello.test

import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef

class StepParent (child: Props, probe: ActorRef) extends Actor {
  context.actorOf(child, "child")
  def receive = {
    case msg => probe.tell(msg, sender)
  }
  
}