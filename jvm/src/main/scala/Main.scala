import java.net.URL
import java.util.concurrent.Executors

import scala._
import scala.concurrent._
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps
import scala.util.Try

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorMaterializer, scaladsl}
import akka.stream.scaladsl.{Flow, Sink, Source}
import net.ruippeixotog.scalascraper.browser.{Browser, JsoupBrowser}
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.model.Document
import upickle._

import Adapter.ec
import proto._

object Main extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  system.whenTerminated andThen { case f: Try[_] => println("Actor system have been terminated") }

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
    path("jobsscan-fastopt.js")(getFromFile("js/target/scala-2.11/jobsscan-fastopt.js"))

  val bindingFuture = Http().bindAndHandle(route, interface = "localhost", port = 8080)

  println("Server started 🚀 ")
  println("Press RETURN to stop...")
  StdIn.readLine() // let it run until user presses return

  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate())

}

object Scanner {
  def all(keyword: String) = Set(
    new SimpleScanner(keyword)
  )

  def scan(keyword: String, candidates: Set[URL]) =
    all(keyword).map(_.scan(candidates)).fold(Set.empty)(_ ++ _)
}

trait Scanner {
  val searchCriteria: String

  def scan(candidates: Set[URL]): Set[Future[URL]] = candidates map { url =>
    Future {
      if (isCandidateAcceptable(url)) url
      else throw new Exception(s"$url doesn't contain $searchCriteria")
    }
  }

  protected def isCandidateAcceptable(candidate: URL): Boolean
}

class SimpleScanner(search: String) extends Scanner {
  override val searchCriteria = search

  override protected def isCandidateAcceptable(canidate: URL) =
    JsoupBrowser().get(canidate.toString()).body.innerHtml.contains(searchCriteria)
}

object Adapter {

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(100))

  def adapters(keyword: String) = List(new YCombinator(20), new Rabotaua(20, keyword), new Stackoverflow(20, keyword))

  def all(keyword: String): Future[Set[URL]] = Future.fold(adapters(keyword).map(_.candidates))(List.empty[URL])(_ ::: _) map { _.toSet }

  def allChecked(keyword: String): Future[Set[Future[URL]]] =
    Future.fold(adapters(keyword).map(_.checked))(Set.empty[Future[URL]])(_ union _)
}

trait Adapter {
  protected val startingPoint: URL
  protected val maxPages: Int
  val scanner = new SimpleScanner("Scala")

  def checked: Future[Set[Future[URL]]] =
    candidates map { _.toSet } map { scanner.scan }

  def candidates: Future[List[URL]] = getCandidateLinks() map { _._1 }

  private def getCandidateLinks(from: URL = startingPoint, currentPage: Int = 1): Future[(List[URL], Int)] = {
    def loop(url: URL, candidates: List[URL]) =
      Future.fold(List(getCandidateLinks(url, currentPage + 1)))((candidates, currentPage)) {
        case (l1, l2) => {
          (l1._1 ::: l2._1, currentPage)
        }
      }

    extractLinksFrom(from) flatMap {
      case (candidates: List[URL], nextPage: Option[URL]) =>
        nextPage
          .flatMap { u: URL => if (currentPage < maxPages) Some(loop(u, candidates)) else None }
          .getOrElse { Future successful ((candidates, currentPage)) }
    }
  }

  private def extractLinksFrom(location: URL): Future[(List[URL], Option[URL])] = Future {
    Try(getBrowserInstance.get(location.toString()))
      .map { doc => (doExtractLinks(doc), nextPage(doc)) }
      .getOrElse { (List.empty, None) }
  }

  protected def getBrowserInstance: Browser = JsoupBrowser()
  protected def doExtractLinks(doc: Document): List[URL]
  protected def nextPage(doc: Document): Option[URL]
}

class YCombinator(override val maxPages: Int) extends Adapter {

  val domain: String = "https://news.ycombinator.com/"
  override protected val startingPoint = new URL(domain + "jobs")

  override def doExtractLinks(doc: Document) = for {
    link <- extractLinks(doc)
    url <- Try { new URL(if (link.startsWith("/")) domain + link else link) } toOption
  } yield url

  override def nextPage(doc: Document) =
    extractLinks(doc)
      .filter { _.contains("jobs?next") }
      .headOption
      .flatMap { s: String => Try { new URL(domain + s) } toOption }

  private def extractLinks(doc: Document): List[String] = for {
    title <- doc >> elementList("td.title")
    link <- title >?> attr("href")("a")
  } yield link
}

class Rabotaua(override val maxPages: Int, val keyword: String) extends Adapter {

  val domain = "http://rabota.ua"
  override protected val startingPoint = new URL(domain + "/jobsearch/vacancy_list?regionId=21&keyWords=" + keyword + "&searchdesc=true")

  override protected def doExtractLinks(doc: Document): List[URL] = for {
    title <- doc >> elementList("tr a.t")
    link <- title >?> attr("href")("a")
    url <- Try { new URL(domain + link) } toOption
  } yield url

  override def nextPage(doc: Document) =
    doc >?> element("a#beforeContentZone_vacancyList_gridList_linkNext") map { h => new URL(domain + h.attr("href")) }

}

class Stackoverflow(override val maxPages: Int, val keyword: String) extends Adapter {

  val domain = "http://stackoverflow.com"
  override protected val startingPoint = new URL(domain + "/jobs?allowsremote=true&searchTerm=" + keyword)

  override protected def doExtractLinks(doc: Document): List[URL] = for {
    title <- doc >> elementList("h1 a.job-link")
    link <- title >?> attr("href")("a")
    url <- Try { new URL(link) } toOption
  } yield url

  override def nextPage(doc: Document) = {
    val links = (doc >?> elementList(".pagination a") last)

    Try { links.map(h => new URL(domain + h.attr("href"))).head } toOption
  }

}
