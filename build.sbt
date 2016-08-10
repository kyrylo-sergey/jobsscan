scalaVersion in ThisBuild := "2.11.8"

scalacOptions ++= Seq("-deprecation")

lazy val jobsscan = crossProject.in(file(".")).
  settings(
    name := "jobsscan",
    version := "0.1-SNAPSHOT",
    publish := {},
    publishLocal := {},
    resolvers += "webjars" at "http://webjars.github.com/m2"
  ).
  jvmSettings(
    libraryDependencies ++= Seq(
      "net.ruippeixotog" %% "scala-scraper" % "1.0.0",
      "com.typesafe.akka" %% "akka-http-experimental" % "2.4.9-RC1",
      "org.specs2" %% "specs2-core" % "3.8.3" % "test",
      "org.specs2" %% "specs2-mock" % "3.8.3" % "test",
      "com.lihaoyi" %%% "upickle" % "0.4.1",
      "com.storm-enroute" %% "scalameter" % "0.6"
    )
  ).
  jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.0",
      "com.lihaoyi" %%% "upickle" % "0.4.1",
      "org.webjars.bower" % "skeleton-css" % "2.0.4"
    )
  )

lazy val server = jobsscan.jvm
lazy val client = jobsscan.js

lazy val root = project.in(file(".")).
  aggregate(server, client).
  settings(
    name := "root",
    publish := {},
    publishLocal := {}
  )
