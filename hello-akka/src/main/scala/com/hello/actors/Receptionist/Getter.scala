package com.hello.actors.Receptionist

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContextExecutor

import org.jsoup.Jsoup

import akka.actor.Actor
import akka.actor.Status
import akka.pattern._
import akka.actor.ActorLogging


object Getter {
  sealed trait GetterEvent
  case object Done extends GetterEvent
  case object Abort extends GetterEvent
}

class Getter(url: String, depth: Int) extends Actor with ActorLogging {
  import Getter._

  implicit val exec: ExecutionContextExecutor = context.dispatcher

   WebClient get url pipeTo self   

  def receive = {
    
    case body: String =>
      for (link <- findLinks(body))
        context.parent ! Controller.Check(link, depth)
        stop()
    case _: Status.Failure => stop() 
    case Abort             => stop()
  }

  def stop() = {
    context.parent ! Done
    context.stop(self)
  }

  def findLinks(body: String): Iterator[String] = {
    val document = Jsoup.parse(body, url)
    val links = document.select("a[href]")

    for {
      link <- links.iterator().asScala
    } yield link.absUrl("href")
  }

}