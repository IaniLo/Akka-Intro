package com.hello.actors.Receptionist

import akka.actor.{Actor, ActorRef, Props}
import akka.actor.ActorLogging

object Receptionist {
  type Jobs = Vector[Job]
  case class Job(client: ActorRef, url: String)

  sealed trait ReceptionistEvent
  case class Failed(url: String) extends ReceptionistEvent
  case class Get(url: String)    extends ReceptionistEvent
  case class Result(url: String, links: Set[String]) extends ReceptionistEvent
}

class Receptionist extends Actor{
  import Receptionist._

  var reqNo = 0
  def runNext(queue: Jobs): Receive = {
    reqNo += 1
    if (queue.isEmpty) waiting
    else {
      val controller = context.actorOf(Props[Controller], s"c$reqNo")
      controller ! Controller.Check(queue.head.url, 2)
      running(queue)
    }
  }

  def enqueueJob(queue: Jobs, job: Job): Receive = {
    if (queue.size > 3) {
      sender ! Failed(job.url)
      running(queue)
    } else running(queue :+ job)
  }

  def receive = waiting

  val waiting: Receive = {
    // upon Get(url) start a traversal and become running
    case Get(url) => 
      context.become(runNext(Vector(Job(sender, url))))
  }

  def running(queue: Jobs): Receive = {
    // upon Get(url) apppend that to queue and keep running
    // upon Controller.Result(links) ship that to client
    // and run next job from queue (if any)
    case Controller.Result(links) =>
        val job = queue.head
        job.client ! Result(job.url, links)
        context.stop(sender)
        context.become(runNext(queue.tail))
    case Get(url) => 
      context.become(enqueueJob(queue, Job(sender, url)))
  }
}


