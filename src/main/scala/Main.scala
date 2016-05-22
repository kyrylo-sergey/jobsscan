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

  doTheJob onComplete { case Success(list) => list.filter(_.isDefined).map(_.get).toSet.map(println) }
}

object Adapter {

  val adapters = List(new YCombinator(5))

  def all: Future[List[URL]] = Future.fold(adapters.map(_.getCandidateLinks().map(_._1)))(List.empty[URL]) { _ ::: _ }
}

trait Adapter {
  protected val startingPoint: URL
  protected val maxPages: Int = 5

  def getCandidateLinks(from: URL = startingPoint, currentPage: Int = 1): Future[(List[URL], Int)] =
    extractLinksFrom(from) flatMap {
      case (candidates: List[URL], nextPage: Option[URL]) =>
        nextPage.flatMap { url: URL =>
          if (currentPage < maxPages) {
            println(currentPage)
            Some(Future.fold(List(getCandidateLinks(url, currentPage + 1)))( (candidates, currentPage) ) { case (l1, l2) =>
              (l1._1 ::: l2._1, currentPage)
            })
          } else None
        } getOrElse { Future successful( (candidates, currentPage)) }
    }

  private def extractLinksFrom(location: URL): Future[(List[URL], Option[URL])] = {
    Future {
      Try { getBrowserInstance.get(location.toString()) } toOption
    }
  } map { docOpt: Option[Document] => docOpt.map { doc: Document => (doExctractLinks(doc), nextPage(doc)) } getOrElse { (List.empty, None)}}

  protected def getBrowserInstance: Browser = JsoupBrowser()
  protected def doExctractLinks(doc: Document): List[URL]
  protected def nextPage(doc: Document): Option[URL]
}

class YCombinator(override val maxPages: Int) extends Adapter {

  val domain: String = "https://news.ycombinator.com/"
  override protected val startingPoint = new URL(domain + "jobs")

  override def doExctractLinks(doc: Document) =
    extractLinks(doc)
      .map { url: Option[String] =>
        url flatMap { s: String =>
          Try { new URL(s) } toOption // TODO: relative links will now work here
        }
      }
      .filter(_.isDefined)
      .map(_.get)

  override def nextPage(doc: Document) =
    extractLinks(doc)
      .filter { s: Option[String] => s.isDefined && s.get.contains("jobs?next") }
      .head
      .flatMap { s: String => Try { new URL(domain + s) } toOption }

  private def extractLinks(doc: Document): List[Option[String]] =
    doc >> elementList("td.title") >?> attr("href")("a")
}
