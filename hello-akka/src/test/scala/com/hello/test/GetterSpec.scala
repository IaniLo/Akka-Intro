package com.hello.test

import java.util.concurrent.Executor
import scala.concurrent.Future
import akka.testkit.TestKit
import akka.actor.ActorSystem
import akka.actor.Props
import scala.concurrent.duration._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import akka.testkit.{ TestActors, DefaultTimeout, ImplicitSender, TestKit }
import com.hello.actors.Receptionist.Controller




object GetterSpec {

  val firstLink = "http://google.com"

  val bodies = Map(
    firstLink ->
      """<html>
    | <head><title> Page 1 </title></head>
    | <body>
		|  <h1> A Link</h1>
		|  <a href="http://google.com/2"</a>
		|</body>
	</html>""".stripMargin)

  val links = Map(firstLink -> Seq("http://google.com/2"))

  object FackeWebClient extends WebClient {
    def get(url: String)(implicit exec: Executor): Future[String] =
      bodies get url match {
        case None       => Future.failed(BadStatus(44))
        case Some(body) => Future.successful(body)
      }

  }

  def fakeGetter(url: String, depth: Int): Props =
    Props(new Getter(url, depth) {
      override def client = FackeWebClient
    })
}

class GetterSpec extends TestKit(ActorSystem("GetterSpec"))
    with WordSpecLike with BeforeAndAfterAll with ImplicitSender {
  import GetterSpec._

  override def afterAll(): Unit = {
    system.terminate()
  }

  "A getter" must {

    "return the right body" in {
      val getter = system.actorOf(Props(new StepParent(fakeGetter(firstLink, 2), testActor)), "rightBody")
      for (link <- links(firstLink))
        expectMsg(Controller.Check(link, 2))
      expectMsg(Getter.Done)
    }

    "properly finish in case of errors" in {
      val getter = system.actorOf(Props(new StepParent(fakeGetter("unknown", 2), testActor)), "wrongLink")
      expectMsg(Getter.Done)
    }
  }

}