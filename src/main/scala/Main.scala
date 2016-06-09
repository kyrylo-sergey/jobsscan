import scala.concurrent.ExecutionContext.Implicits._
import net.ruippeixotog.scalascraper.model.Document
import java.net.URL
import scala.concurrent.Future
import scala.util.Success
import net.ruippeixotog.scalascraper.browser.{ JsoupBrowser, Browser }
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import scala.language.postfixOps
import scala.util.Try
import scala.concurrent.duration._
import scala.concurrent._

object Main extends App {

  val keyword: String = "Scala"
  val domain: String = "https://news.ycombinator.com/"

  def doTheJob: Future[List[Option[URL]]] = {
    val linksPromise = Adapter.all

    val foundPages: Future[List[Option[URL]]] = linksPromise
      .flatMap { candidateLinks: List[URL] =>
        Future.sequence {
          candidateLinks
            .map { candidateLink: URL =>
              Future { Try { JsoupBrowser().get(candidateLink.toString()) }.toOption }
                .map { o: Option[Document] =>
                  o.flatMap {
                    d: Document => if (d.body.innerHtml.contains(keyword)) Some(candidateLink) else None
                  }
                }
            }
        }
      }

    foundPages
  }

  val res = doTheJob
  res onComplete { case Success(list) => list.filter(_.isDefined).map(_.get).toSet.map(println) }

  Await.result(res, 1 hour)
}

object Adapter {

  val adapters = List(new YCombinator(2))

  def all: Future[List[URL]] = Future.fold(adapters.map(_.candidates))(List.empty[URL])(_ ::: _)
}

trait Adapter {
  protected val startingPoint: URL
  protected val maxPages: Int

  def candidates: Future[List[URL]] = getCandidateLinks() map { _._1 }

  private def getCandidateLinks(from: URL = startingPoint, currentPage: Int = 1): Future[(List[URL], Int)] = {
    def loop(url: URL, candidates: List[URL]) =
      Future.fold(List(getCandidateLinks(url, currentPage + 1)))( (candidates, currentPage) ){ case (l1, l2) =>
        (l1._1 ::: l2._1, currentPage)
      }

    extractLinksFrom(from) flatMap { case (candidates: List[URL], nextPage: Option[URL]) =>
      nextPage
        .flatMap { u: URL => if (currentPage < maxPages) Some(loop(u, candidates)) else None }
        .getOrElse { Future successful( (candidates, currentPage)) }
    }
  }

  private def extractLinksFrom(location: URL): Future[(List[URL], Option[URL])] = Future {
    Try(getBrowserInstance.get(location.toString()))
      .map { doc => (doExctractLinks(doc), nextPage(doc)) }
      .getOrElse { (List.empty, None) }
    }

  protected def getBrowserInstance: Browser = JsoupBrowser()
  protected def doExctractLinks(doc: Document): List[URL]
  protected def nextPage(doc: Document): Option[URL]
}

class YCombinator(override val maxPages: Int) extends Adapter {

  val domain: String = "https://news.ycombinator.com/"
  override protected val startingPoint = new URL(domain + "jobs")

  override def doExctractLinks(doc: Document) = for {
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
