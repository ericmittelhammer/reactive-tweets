name := "reactive-tweets"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  "com.typesafe.akka" %% "akka-testkit" % "2.2.0" % "test",
  "org.scalatest" % "scalatest_2.10" % "2.0" % "test"
)     

play.Project.playScalaSettings

scalariformSettings

org.scalastyle.sbt.ScalastylePlugin.Settings
