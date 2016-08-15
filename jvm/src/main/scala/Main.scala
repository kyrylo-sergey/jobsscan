import java.net.URL

import scala._
import scala.concurrent._
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Success, Failure}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorMaterializer}
import akka.stream.scaladsl.{Flow, Source}
import upickle._

import proto._
import crawler._

object Main extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  // TODO; handle streamed and binary messages
  def protoMessageFlow: Flow[Message, ProtoMessage, akka.NotUsed] =
    Flow[Message]
      .collect { case TextMessage.Strict(msg) => msg }
      .map { msg =>
        default.read[(String, String)](msg) match {
          case ("StartSearch", term) => StartSearch(term)
          case _ => NotSupported(msg)
        }
      }

  def websocketHandler: Flow[Message, Message, Any] =
    Flow[Message]
      .via(protoMessageFlow)
      .mapConcat {
        case StartSearch(keyword) => {

          println("Started searching for candidates")

          // TODO: remove blocking
          val listOfScannedLinks = Await.result(Adapter.allChecked(keyword), 1 hour)

          println(s"Found ${listOfScannedLinks.size} candidates. Looking for $keyword in those")

          def futureURLToJson(f: Future[URL]) = f
            .map { (u: URL) => ("SuccessfulCandidate", u.toString()) }
            .recover { case t: Throwable => ("FailedCandidate", t.toString()) }
            .map { default.write(_) }

          listOfScannedLinks map { f: Future[URL] =>
            TextMessage(Source.fromFuture { futureURLToJson(f) })
          }
        }
        case NotSupported(msg) => List(TextMessage(Source.single(s"not supported message: $msg")))
        case _ => List(TextMessage(Source.single("unknown message type")))
      }

  import Directives._

  val route = get {
    pathEndOrSingleSlash {
      getFromFile("js/index.html")
    }
  } ~
    path("ws-echo") {
      get {
        handleWebSocketMessages(websocketHandler)
      }
    } ~
    path("jobsscan-fastopt.js")(getFromFile("js/target/scala-2.11/jobsscan-fastopt.js")) ~
    path("jobsscan-deps.js")(getFromFile("js/target/scala-2.11/jobsscan-jsdeps.js"))

  val bindingFuture = Http().bindAndHandle(route, interface = "localhost", port = 8080)

  bindingFuture.onComplete {
    case Success(binding) =>
      val localAddress = binding.localAddress
      println(s"🚀  Server is listening on ${localAddress.getHostName}:${localAddress.getPort}. Press RETURN to stop...")
      StdIn.readLine() // let it run until user presses return
      bindingFuture
        .flatMap(_.unbind()) // trigger unbinding from the port
        .onComplete(_ => system.terminate())

    case Failure(e) =>
      println(s"Binding failed with ${e.getMessage}")
      system.shutdown()
  }
}
