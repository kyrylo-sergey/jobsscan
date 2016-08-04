name := "jobsscan"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "net.ruippeixotog" %% "scala-scraper" % "1.0.0",
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.9-RC1",
  "org.specs2" %% "specs2-core" % "3.8.3" % "test",
  "org.specs2" %% "specs2-mock" % "3.8.3" % "test"
)
