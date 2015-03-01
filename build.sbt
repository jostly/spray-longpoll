
organization  := "com.example"

version       := "0.1"

scalaVersion  := "2.11.5"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/"
)

libraryDependencies ++= Seq(
  "io.spray"            %%  "spray-can"     % "1.3.1",
  "io.spray"            %%  "spray-routing" % "1.3.1",
  "io.spray"            %%  "spray-testkit" % "1.3.1",
  "io.spray"            %%  "spray-json"    % "1.3.1",
  "org.json4s"          %%  "json4s-native" % "3.2.11",
  "com.typesafe.akka"   %%  "akka-actor"    % "2.3.2",
  "org.specs2"          %%  "specs2"        % "2.3.12" % "test"
)

Revolver.settings

seq(com.typesafe.sbt.SbtStartScript.startScriptForClassesSettings: _*)

