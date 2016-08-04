name := "jobsscan"

scalaVersion := "2.11.8"

lazy val jobsscan = project

lazy val common = (
  Project("common", file("common"))
    settings(
      scalaVersion := "2.11.8",
      libraryDependencies ++= Seq(
        "org.scala-js" %% "scalajs-stubs" % "0.6.11" % "provided"
      )
    )
)

lazy val server = (
  Project("server", file("server"))
    settings(
      scalaVersion := "2.11.8",
      libraryDependencies ++= Seq(
        "net.ruippeixotog" %% "scala-scraper" % "1.0.0",
        "com.typesafe.akka" %% "akka-http-experimental" % "2.4.9-RC1",
        "org.specs2" %% "specs2-core" % "3.8.3" % "test",
        "org.specs2" %% "specs2-mock" % "3.8.3" % "test"
      )
    )
)

lazy val client = (
  Project("client", file("client"))
    enablePlugins(ScalaJSPlugin)
    dependsOn(common)
    settings(
      scalaVersion := "2.11.8",
      libraryDependencies ++= Seq(
        "org.scala-js" %%% "scalajs-dom" % "0.9.0"
      )
    )
    settings(
      unmanagedSourceDirectories in Compile <++= (unmanagedSourceDirectories in jobsscan) in Compile
    )
)
