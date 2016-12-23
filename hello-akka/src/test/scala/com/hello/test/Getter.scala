package com.hello.test

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContextExecutor

import org.jsoup.Jsoup

import akka.actor.Actor
import akka.actor.Status
import akka.pattern._
import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext
import com.hello.actors.Receptionist.Controller




object Getter {
  sealed trait GetterEvent
  case object Done extends GetterEvent
  case object Abort extends GetterEvent
}

class Getter(url: String, depth: Int) extends Actor {
  import Getter._

//  implicit val exec: ExecutionContextExecutor = context.dispatcher
  implicit val executor = context.dispatcher.asInstanceOf[Executor with ExecutionContext]
  def client: WebClient = AsyncWebClient //for testing
  
  
   client get url pipeTo self   //  same as
//   WebClient get url pipeTo self   //  same as


  def receive = {
    case body: String =>
      for (link <- findLinks(body))
        context.parent ! Controller.Check(link, depth)
        stop()
    case Abort             => stop()
    case _: Status.Failure => stop() 
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