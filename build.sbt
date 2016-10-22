import NativePackagerHelper._

scalaVersion in ThisBuild := "2.11.8"

lazy val jobsscan = crossProject.in(file(".")).
  enablePlugins(JavaAppPackaging).
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
      "com.lihaoyi" %% "scalatags" % "0.6.0",
      "org.typelevel" %% "cats" % "0.7.2"
    )
  ).
  jsSettings(
    skip in packageJSDependencies := false,
    scalaJSUseRhino in Global := false,
    libraryDependencies := {
      libraryDependencies.value.filterNot(_.organization == "org.scoverage") ++ Seq(
        "org.scala-js" %%% "scalajs-dom" % "0.9.0",
        "com.lihaoyi" %%% "upickle" % "0.4.1",
        "be.doeraene" %%% "scalajs-jquery" % "0.9.0",
        "com.github.japgolly.scalajs-react" %%% "core" % "0.11.2"
      )
    },
    jsDependencies ++= Seq(
      RuntimeDOM,
      "org.webjars" % "jquery" % "3.1.1" / "3.1.1/dist/jquery.min.js",
      "org.webjars.bower" % "materialize" % "0.97.6" / "0.97.6/dist/js/materialize.min.js" dependsOn "dist/jquery.min.js",

        "org.webjars.bower" % "react" % "15.3.2"
        /        "react-with-addons.js"
        minified "react-with-addons.min.js"
        commonJSName "React",

        "org.webjars.bower" % "react" % "15.3.2"
          /         "react-dom.js"
          minified  "react-dom.min.js"
          dependsOn "react-with-addons.js"
          commonJSName "ReactDOM",

        "org.webjars.bower" % "react" % "15.3.2"
          /         "react-dom-server.js"
          minified  "react-dom-server.min.js"
          dependsOn "react-dom.js"
          commonJSName "ReactDOMServer"
      ),
    artifactPath in Compile in fastOptJS := (crossTarget in fastOptJS).value / ((moduleName in fastOptJS).value + ".js"),
    artifactPath in Compile in fullOptJS := (crossTarget in fullOptJS).value / ((moduleName in fullOptJS).value + ".js"),
    artifactPath in Compile in packageJSDependencies :=
      ((crossTarget in packageJSDependencies).value /
        ((moduleName in packageJSDependencies).value + "-deps.js")),
    artifactPath in Compile in packageMinifiedJSDependencies :=
      ((crossTarget in packageMinifiedJSDependencies).value /
        ((moduleName in packageMinifiedJSDependencies).value + "-deps.js"))
  )

lazy val server = jobsscan.jvm.settings(
  (resources in Compile) += (fastOptJS in (client, Compile)).value.data,
  (resources in Compile) += (packageJSDependencies in (client, Compile)).value
)
lazy val client = jobsscan.js

lazy val root = project.in(file(".")).
  aggregate(server, client).
  settings(
    name := "root",
    publish := {},
    publishLocal := {}
  )
