name := "jobsscan"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "net.ruippeixotog" %% "scala-scraper" % "1.0.0",
  "org.specs2" %% "specs2-core" % "3.8.3" % "test",
  "org.specs2" %% "specs2-mock" % "3.8.3" % "test",
  "com.typesafe.akka" %% "akka-stream" % "2.4.6"
)
