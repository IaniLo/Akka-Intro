package com.hello.test

import akka.actor.Actor
import akka.testkit.TestKit
import akka.testkit.TestProbe
import akka.testkit.ImplicitSender
import akka.actor.ActorSystem
import akka.testkit.TestActorRef
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import akka.actor.Props
import scala.concurrent.duration._

class ToggleSpec extends TestKit(ActorSystem("TestSys")) 
with ImplicitSender with WordSpecLike with Matchers {

  "Toggle Actor" should {
    
    "be happy at first" in {
      
      implicit val system = ActorSystem("TestSys")

      val toggle = system.actorOf(Props[Toggle])

//      val p = TestProbe()
      toggle ! "How are you?"
      expectMsg("happy")
      
//      p.send(toggle, "How are you?")
//      p.expectMsg("happy")

      toggle ! "How are you?"
      expectMsg("sad")
      
      toggle ! "unknown"
      expectNoMsg(1.second)

      system.terminate()
    }

    "then, actor will be sad" in {
      // using ImplicitSender, TestKit,
      val toggle = TestActorRef[Toggle]

      toggle ! "How are you?"
      expectMsg("happy")
      
      toggle ! "How are you?"
      expectMsg("sad")
    }
  }
}


class Toggle extends Actor {
  
  def happy: Receive = {
    case "How are you?" => sender ! "happy"
      context become sad
  }
  
  def sad: Receive = {
    case "How are you?" => sender ! "sad"
      context become happy
  }
  
  def receive = happy
}