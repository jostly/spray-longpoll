package com.example

import akka.actor._
import scala.concurrent.ExecutionContext.Implicits.global
import spray.routing._
import spray.http._
import com.example.Implicits._
import java.util.UUID
import spray.routing.directives.LogEntry
import akka.event.Logging

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class MyServiceActor extends Actor with MyService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)
}

/**
 * BroadcastMessage used when some data needs to be sent to all
 * alive clients
 * @param data
 */
case class BroadcastMessage(data: HttpEntity)

/**
 * Poll used when a client wants to register/long-poll with the
 * server
 */
case class Poll(id: String, reqCtx: RequestContext)

/**
 * PollTimeout sent when a long-poll requests times out
 */
case class PollTimeout(id: String)

class CometActor extends Actor {

  var toTimers: Map[String, Cancellable] = Map.empty      // list of timeout timers for clients
  var requests: Map[String, RequestContext] = Map.empty   // list of long-poll RequestContexts

  val conf = context.system.settings.config.getConfig("comet-actor")

  val clientTimeout = conf.getDuration("client-timeout")            // long-poll requests are closed after this much time, clients reconnect after this
  val rescheduleDuration = conf.getDuration("reschedule-duration")  // reschedule time for alive client which hasnt polled since last message

  def receive = {
    case Poll(id, reqCtx) =>
      requests += (id -> reqCtx)
      toTimers.get(id).map(_.cancel())
      toTimers += (id -> context.system.scheduler.scheduleOnce(clientTimeout, self, PollTimeout(id)))

    case PollTimeout(id) =>
      requests.get(id).map(_.complete(HttpResponse(StatusCodes.OK)))
      requests -= id
      toTimers -= id

    case BroadcastMessage(data) =>
      requests.values.foreach { ctx =>
        ctx.complete(HttpResponse(entity = data))
      }

      toTimers.map(_._2.cancel)
      toTimers = Map.empty
      requests = Map.empty
  }
}

// this trait defines our service behavior independently from the service actor
trait MyService extends HttpService with TwirlSupport {

  val cometActor = actorRefFactory.actorOf(Props[CometActor])

  def showRepoResponses(request: HttpRequest): HttpResponsePart ⇒ Option[LogEntry] = {
    case HttpResponse(s, _, _, _) ⇒ Some(LogEntry(s"${s.intValue}: ${request.uri}", Logging.DebugLevel))
    case _ ⇒ None
  }

  val myRoute =
    logRequestResponse(showRepoResponses _) {
      path("") {
        get {
          complete(html.page())
        }
      } ~
      path("comet") {
        get {
          cometActor ! Poll(UUID.randomUUID.toString, _)
        }
      } ~
      path("sendMessage") {
        get {
          parameters('name, 'message) { (name: String, message: String) =>
            cometActor ! BroadcastMessage(HttpEntity("%s : %s".format(name, message)))
            complete(StatusCodes.OK)
          }
        }
      } ~
      pathPrefix("static" / Segment) { dir =>
        getFromResourceDirectory(dir)
      } ~
      pathPrefixTest(Segment) { file =>
        getFromResourceDirectory("webroot")
      }
    }
}
