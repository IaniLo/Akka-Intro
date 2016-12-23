package com.hello.test

import java.util.concurrent.Executor
import scala.concurrent.Promise
import scala.concurrent.Future
import com.ning.http.client.AsyncHttpClient

trait WebClient {
  def get(url: String)(implicit exec: Executor): Future[String]
}

case class BadStatus(statusCode: Int) extends RuntimeException

object AsyncWebClient extends WebClient{

  val client = new AsyncHttpClient

  def get(url: String)(implicit exec: Executor): Future[String] = {

    //java Feature
    val f = client.prepareGet(url).execute()
    //scala Promise
    val p = Promise[String]()
    f.addListener(new Runnable {
      def run = {
        val response = f.get
        if (response.getStatusCode /100 < 4)
          p.success(response.getResponseBodyExcerpt(131072))
        else p.failure(BadStatus(response.getStatusCode))
      }
    }, exec)
    p.future
  }

  def shutdown(): Unit = client.close()
  
}