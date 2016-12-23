package com.hello.actors.Receptionist

import java.util.concurrent.Executor

import scala.concurrent.Future
import scala.concurrent.Promise

import com.ning.http.client.AsyncHttpClient



 case class BadStatus(statusCode: Int) extends RuntimeException

object WebClient {

  private val client = new AsyncHttpClient

  def get(url: String)(implicit exec: Executor): Future[String] = {
    val f = client.prepareGet(url).execute()
    val p = Promise[String]()
    f.addListener(new Runnable {
      def run = {
        val response = f.get
        if (response.getStatusCode < 400)
          p.success(response.getResponseBodyExcerpt(131072))
        else p.failure(BadStatus(response.getStatusCode))
      }
    }, exec)
    p.future
  }

  def shutdown(): Unit = client.close()
}