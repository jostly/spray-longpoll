package com.example

import com.typesafe.config.Config
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object Implicits {

  implicit class RichConfig(c: Config) {
    def getDuration(path: String) = {
      FiniteDuration(c.getDuration(path, TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS)
    }
  }

}
