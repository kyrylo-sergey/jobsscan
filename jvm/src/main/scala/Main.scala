import java.net.URL

import scala._
import scala.concurrent._
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Success, Failure}
import scala.language.implicitConversions

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.stream.{ActorMaterializer}
import akka.stream.scaladsl.{Flow, Source}
import upickle._
import scalatags.Text.all._

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

          val scanner = new SimpleScanner(keyword)

          implicit def futureToSource[T](f: Future[T]): Source[T, akka.NotUsed] = Source.fromFuture(f)

          // TODO: remove blocking
          val listOfScannedLinks = Await.result(Adapter.allChecked(keyword, scanner), 1 hour)

          println(s"Found ${listOfScannedLinks.size} candidates. Looking for $keyword in those")

          def futureURLToJson(f: Future[URL]) = f
            .map { (u: URL) => ("SuccessfulCandidate", u.toString()) }
            .recover { case t: Throwable => ("FailedCandidate", t.toString()) }
            .map { default.write(_) }

          List(TextMessage(default.write(("CandidatesCount", listOfScannedLinks.size.toString())))) ++
            (listOfScannedLinks map { f: Future[URL] => TextMessage(futureURLToJson(f)) }).toList
        }
        case NotSupported(msg) => List(TextMessage(s"not supported message: $msg"))
        case _ => List(TextMessage("unknown message type"))
      }

  import Directives._

  val route =
    get {
      pathEndOrSingleSlash {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, Templates.index.render))
      } ~
        path("ws-echo") {
          handleWebSocketMessages(websocketHandler)
        }
    } ~
      getFromResourceDirectory("")

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
      system.terminate()
  }
}

object Templates {

  def index =
    html(
      head(
        title := "JobsScan",
        link(
          rel := "stylesheet",
          href := "https://cdnjs.cloudflare.com/ajax/libs/materialize/0.97.7/css/materialize.min.css"
        )
      ),
      body(
        div(
          `class` := "container",
          div(
            `class` := "row",
            div(`class` := "col s9", id := "header",
              div(
                `class` := "row",
                h1("JobsScan"), h6("a better way to find your dream job ~>")
              ))
          ),
          div(
            `class` := "row",
            div(`class` := "col s9", id := "content",
              div(
                `class` := "row",
                div(`class` := "col s6", input(id := "search-box", `type` := "text", name := "search")),
                div(`class` := "col s3", a("Search", id := "search-btn", `class` := "waves-effect waves-light btn"))
              ),
              div(id := "progress", `class` := "row"),
              div(id := "links", `class` := "row"))
          )
        ),
        script(`type` := "text/javascript", src := "jobsscan.js"),
        script(`type` := "text/javascript", src := "jobsscan-deps.js"),
        script("new Client().main();", `type` := "text/javascript")
      )
    )
}
