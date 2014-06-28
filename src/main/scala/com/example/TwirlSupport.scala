package com.example

import spray.http.MediaTypes._
import spray.http.{ContentType, ContentTypes}
import spray.httpx.marshalling.Marshaller
import play.twirl.api.{Html, Txt, Xml}

/**
 * A trait providing Marshallers for the Twirl template result types.
 *
 * This is copied from spray-httpx_2.11/bundles/spray-httpx_2.11-1.3.1.jar!/spray/httpx/TwirlSupport.class
 * because spray-httpx_2.11 depends on twirl-api version 0.7.0 and we are using 1.0.2 in this project.
 */

trait TwirlSupport {

  implicit val twirlHtmlMarshaller =
    twirlMarshaller[Html](`text/html`, `application/xhtml+xml`)

  implicit val twirlTxtMarshaller =
    twirlMarshaller[Txt](ContentTypes.`text/plain`)

  implicit val twirlXmlMarshaller =
    twirlMarshaller[Xml](`text/xml`)

  protected def twirlMarshaller[T](marshalTo: ContentType*): Marshaller[T] =
    Marshaller.delegate[T, String](marshalTo: _*)(_.toString)
}

object TwirlSupport extends TwirlSupport
