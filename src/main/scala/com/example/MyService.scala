package com.example

import akka.actor._
import scala.concurrent.ExecutionContext.Implicits.global
import spray.routing._
import spray.http._
import scala.concurrent.duration._
import com.example.Implicits._

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
 * CometMessage used when some data needs to be sent to a client
 * with the given id
 */
case class CometMessage(id: String, data: HttpEntity, counter: Long = 0)

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

/**
 * ClientGc sent to deregister a client when it hasnt responded
 * in a long time
 */
case class ClientGc(id: String)

class CometActor extends Actor {
  var aliveTimers: Map[String, Cancellable] = Map.empty   // list of timers that keep track of alive clients
  var toTimers: Map[String, Cancellable] = Map.empty      // list of timeout timers for clients
  var requests: Map[String, RequestContext] = Map.empty   // list of long-poll RequestContexts

  val conf = context.system.settings.config.getConfig("comet-actor")

  val gcTime = 1.minute               // if client doesnt respond within this time, its garbage collected
  val clientTimeout = conf.getDuration("client-timeout")            // long-poll requests are closed after this much time, clients reconnect after this
  val rescheduleDuration = conf.getDuration("reschedule-duration")  // reschedule time for alive client which hasnt polled since last message
  val retryCount = 10                 // number of reschedule retries before dropping the message

  def receive = {
    case Poll(id, reqCtx) =>
      requests += (id -> reqCtx)
      toTimers.get(id).map(_.cancel())
      toTimers += (id -> context.system.scheduler.scheduleOnce(clientTimeout, self, PollTimeout(id)))

      aliveTimers.get(id).map(_.cancel())
      aliveTimers += (id -> context.system.scheduler.scheduleOnce(gcTime, self, ClientGc(id)))

    case PollTimeout(id) =>
      requests.get(id).map(_.complete(HttpResponse(StatusCodes.OK)))
      requests -= id
      toTimers -= id

    case ClientGc(id) =>
      requests -= id
      toTimers -= id
      aliveTimers -= id

    case CometMessage(id, data, counter) =>
      val reqCtx = requests.get(id)
      reqCtx.map { reqCtx =>
        reqCtx.complete(HttpResponse(entity=data))
        requests = requests - id
      } getOrElse {
        if(aliveTimers.contains(id) && counter < retryCount) {
          context.system.scheduler.scheduleOnce(rescheduleDuration, self, CometMessage(id, data, counter+1))
        }
      }

    case BroadcastMessage(data) =>
      aliveTimers.keys.map { id =>
        requests.get(id).map(_.complete(HttpResponse(entity=data))).getOrElse(self ! CometMessage(id, data))
      }
      toTimers.map(_._2.cancel)
      toTimers = Map.empty
      requests = Map.empty
  }
}

// this trait defines our service behavior independently from the service actor
trait MyService extends HttpService with TwirlSupport {

  val cometActor = actorRefFactory.actorOf(Props[CometActor])

  val myRoute =
    path("") {
      get {
        complete(html.page())
      }
    } ~
    path("comet") {
      get {
        parameters('id) { id:String =>
          (cometActor ! Poll(id, _))
        }
      }
    } ~
    path("sendMessage") {
      get {
        parameters('name, 'message) { (name:String, message:String) =>
          cometActor ! BroadcastMessage(HttpEntity("%s : %s".format(name, message)))
          complete(StatusCodes.OK)
        }
      }
    } ~
    pathPrefix("static" / Segment) { dir =>
      getFromResourceDirectory(dir)
    } ~
    pathPrefix(Segment) { file =>
      redirect("/static/webroot/" + file, StatusCodes.MovedPermanently)
    }
}
