import scala._
import scala.collection.JavaConverters._
import scala.concurrent._
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.{implicitConversions, postfixOps}
import scala.util.{Failure, Success}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import cache.Cache
import crawler._
import proto._
import shared.Domain
import upickle._

object Main extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  val cache = Cache[Int, Set[Future[CrawlResult]]](1 hour)

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
        case StartSearch(keyword, providers) => {

          println(s"Started searching for candidates for following providers: ${providers.mkString(", ")}")

          val scanner = new SimpleScanner(keyword)

          implicit def futureToSource[T](f: Future[T]): Source[T, akka.NotUsed] = Source.fromFuture(f)

          // TODO: remove blocking
          val listOfScannedLinks = cache.getOrElseUpdate(
            (keyword, providers).##,
            Await.result(Adapter(providers: _*)(5, keyword, scanner), 1 hour)
          )

          println(s"Cache size is ${cache.iterator.size}")

          println(s"Found ${listOfScannedLinks.size} candidates. Looking for $keyword in those")

          def futureURLToJson(f: Future[CrawlResult]) = f
            .map {
              //TODO: simplify this after https://issues.scala-lang.org/browse/SI-7046 is fixed
              case cs: CrawlSuccessful => (Msg.CRAWL_SUCCESSFUL, default.write(cs))
              case cu: CrawlUnsuccessful => (Msg.CRAWL_UNSUCCESSFUL, default.write(cu))
            }
            .map { default.write(_) }

          val completed = Future.sequence(listOfScannedLinks)
            .map { items: Set[CrawlResult] =>
              default.write(Msg.SEARCH_FINISHED -> default.write(SearchFinished(items.filter(_.isInstanceOf[CrawlSuccessful]).size)))
            }

          List(TextMessage(default.write((Msg.CANDIDATES_COUNT, default.write(CandidatesCount(listOfScannedLinks.size)))))) ++
            (listOfScannedLinks map { f: Future[CrawlResult] => TextMessage(futureURLToJson(f)) }).toList ++
            List(TextMessage(completed))
        }
        case NotSupported(msg) => List(TextMessage(s"not supported message: $msg"))
        case _ => List(TextMessage("unknown message type"))
      }

  import Directives._

  val route =
    get {
      pathEndOrSingleSlash {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, Templates.index(Domain.providers.toList).render))
      } ~
        path("ws-echo") {
          handleWebSocketMessages(websocketHandler)
        }
    } ~
      pathPrefix("assets") {
        getFromDirectory("jvm/src/main/webapp/assets/")
      } ~
      getFromResourceDirectory("")

  val env = System.getenv.asScala
  val host = env getOrElse ("JOBSSCANHOST", "localhost")
  val port = env getOrElse ("JOBSSCANPORT", "8085")
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
