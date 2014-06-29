package com.example

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import org.specs2.mutable.{After, Specification}
import org.specs2.mock.Mockito
import org.specs2.time.NoTimeConversions
import spray.routing.RequestContext
import spray.http.{HttpEntity, StatusCodes, HttpResponse}
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

abstract class AkkaTestkitSpecs2Support(sys: ActorSystem) extends TestKit(sys) with After with ImplicitSender {
  // make sure we shut down the actor system after all tests have run
  def after = TestKit.shutdownActorSystem(system)
}

class CometActorSpec extends Specification with NoTimeConversions with Mockito {

  val cfg = ConfigFactory.parseString(
    """
      comet-actor {
        client-timeout = 1s
        reschedule-duration = 200ms
      }
    """)

  "comet actor" should {
    "hold connection for polling time" in new AkkaTestkitSpecs2Support(ActorSystem("CometActorSpec", cfg)) {

      val cometActor = system.actorOf(Props(classOf[CometActor]))

      val ctx = mock[RequestContext]
      cometActor ! Poll("id1", ctx)

      there was MockitoVerificationWithTimeout(1.second).one(ctx).complete(argThat(===(HttpResponse(StatusCodes.OK))))(any)
    }
    "send message back to client when message arrives after poll" in new AkkaTestkitSpecs2Support(ActorSystem("CometActorSpec", cfg)) {
      val data = HttpEntity("data")

      val cometActor = system.actorOf(Props(classOf[CometActor]))

      val ctx = mock[RequestContext]
      cometActor ! Poll("id1", ctx)
      cometActor ! CometMessage("id1", data)

      there was MockitoVerificationWithTimeout(1.second).one(ctx).complete(argThat(===(HttpResponse(entity=data))))(any)
    }
    "send message back to client when message arrives before poll" in new AkkaTestkitSpecs2Support(ActorSystem("CometActorSpec", cfg)) {
      val data = HttpEntity("data")

      val cometActor = system.actorOf(Props(classOf[CometActor]))

      val ctx = mock[RequestContext]

      cometActor ! Poll("id1", ctx)
      there was MockitoVerificationWithTimeout(1.second).one(ctx).complete(argThat(===(HttpResponse(StatusCodes.OK))))(any)

      cometActor ! CometMessage("id1", data)
      cometActor ! Poll("id1", ctx)
      there was MockitoVerificationWithTimeout(1.second).one(ctx).complete(argThat(===(HttpResponse(entity=data))))(any)
    }
    "broadcast message to all pollers" in new AkkaTestkitSpecs2Support(ActorSystem("CometActorSpec", cfg)) {
      val data = HttpEntity("data")

      val cometActor = system.actorOf(Props(classOf[CometActor]))

      val ctx1 = mock[RequestContext]
      val ctx2 = mock[RequestContext]

      cometActor ! Poll("id1", ctx1)
      there was MockitoVerificationWithTimeout(1.second).one(ctx1).complete(argThat(===(HttpResponse(StatusCodes.OK))))(any)

      cometActor ! Poll("id2", ctx2)
      cometActor ! BroadcastMessage(data)
      cometActor ! Poll("id1", ctx1)
      there was MockitoVerificationWithTimeout(1.second).one(ctx2).complete(argThat(===(HttpResponse(entity=data))))(any)
      there was MockitoVerificationWithTimeout(1.second).one(ctx1).complete(argThat(===(HttpResponse(entity=data))))(any)
    }
  }

}
