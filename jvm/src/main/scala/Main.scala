import java.net.URL

import scala._
import scala.concurrent._
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Success, Failure}
import scala.language.implicitConversions
import scala.collection.JavaConverters._

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
          //TODO: simplify this after https://issues.scala-lang.org/browse/SI-7046 is fixed
          case (Msg.START_SEARCH, msg) => default.read[StartSearch](msg)
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

          def futureURLToJson(f: Future[CrawlResult]) = f
            .map { _ match  {
              case cs: CrawlSuccessful => (Msg.CRAWL_SUCCESSFUL, default.write(cs))
              case cu: CrawlUnsuccessful => (Msg.CRAWL_UNSUCCESSFUL, default.write(cu))
            }
          }
//            .recover { case t: Throwable => ("CrawlUnsuccessful", t.toString()) }
            .map { default.write(_) }

          List(TextMessage(default.write((Msg.CANDIDATES_COUNT, default.write(CandidatesCount(listOfScannedLinks.size)))))) ++
            (listOfScannedLinks map { f: Future[CrawlResult] => TextMessage(futureURLToJson(f)) }).toList
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

  val env = System.getenv.asScala
  val host = env getOrElse("JOBSSCANHOST", "localhost")
  val port = env getOrElse("JOBSSCANPORT", "8085")
  val bindingFuture = Http().bindAndHandle(route, interface = host, port = port.toInt)

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
        script(`type` := "text/javascript", src := "jobsscan-deps.js"),
        script(`type` := "text/javascript", src := "jobsscan.js"),
        script("new Client().main();", `type` := "text/javascript")
      )
    )
}
