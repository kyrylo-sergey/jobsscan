import java.util.concurrent.Executors
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.scaladsl.Flow
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import scala.concurrent.ExecutionContext
import net.ruippeixotog.scalascraper.model.Document
import java.net.URL
import scala.concurrent.Future
import scala.util.{Failure, Success}
import net.ruippeixotog.scalascraper.browser.{JsoupBrowser, Browser}
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import scala.language.postfixOps
import scala.util.Try
import scala.concurrent.duration._
import scala.concurrent._
import scala._
import Adapter.ec
//import scala.concurrent.ExecutionContext.Implicits._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl


object Main extends App {

  def weboscketHandler: Flow[Message, Message, Any] =
    Flow[Message].mapConcat {
      case tm: TextMessage =>
        TextMessage(Source.single("Hello ") ++ tm.textStream ++ Source.single("!")) :: Nil
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)
        Nil
    }
  /*  val keyword: String = "Scala"

  //val list: Set[Future[URL]] = Scanner.scan(keyword, Await.result(Adapter.all(keyword), 1 hour))

  val list = Await.result(Adapter.allChecked("Scala"), 1 hour)

  list foreach { f =>
    f.onComplete {
      case Success(url) => println(url)
      case Failure(t) => //println(t)
    }
  }

 def futureToFutureTry[T](f: Future[T]): Future[Try[T]] =
    f.map(Success(_)).recover { case t: Throwable => Failure(t) }

  val f = Future.sequence(list.map(futureToFutureTry(_)))

  Await.ready(f, 1 hour)

  f.map { _.filter(_.isSuccess) } onComplete {
    _ match {
      case Success(l) => println(s"In total found ${l.toSet.size} links")
      case Failure(_) => {}
    }
 }*/

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val serverSource = Http().bind(interface = "localhost", port = 8080)

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      HttpResponse(entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        "<html><body>Hello world!</body></html>"
      ))

    case req @ HttpRequest(GET, Uri.Path("/data"), _, _, _) =>
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) => upgrade.handleMessages(weboscketHandler)
        case None => HttpResponse(400, entity = "Not a valid websocket request!")
      }
    case r: HttpRequest =>
      r.discardEntityBytes()
      HttpResponse(404, entity = "Unknown resource!")
  }

  println("Server started 🚀 ")

  val bindingFuture: Future[Http.ServerBinding] =
    serverSource.to(Sink.foreach { connection =>
      println("Accepted new connection from " + connection.remoteAddress)

      connection handleWithSyncHandler requestHandler
      // this is equivalent to
      // connection handleWith { Flow[HttpRequest] map requestHandler }
    }).run()
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
