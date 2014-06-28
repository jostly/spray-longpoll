package com.example

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http

object Boot extends App {
  implicit val system = ActorSystem("spray-longpoll")

  // create and start our service actor
  val service = system.actorOf(Props[MyServiceActor], "my-service")

  // create a new HttpServer using our handler tell it where to bind to
  IO(Http) ! Http.Bind(service, interface = "localhost", port = 8080)
}
