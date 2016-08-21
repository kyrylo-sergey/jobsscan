package crawler

import java.net.URL
import java.util.concurrent.Executors

import scala._
import scala.concurrent._
import scala.util.Try
import scala.language.postfixOps

import net.ruippeixotog.scalascraper.browser.{Browser, JsoupBrowser}
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.model.Document

import proto._

case class CrawlCandidate(source: String, initialURL: URL, targetURL: URL)

object Adapter {

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(100))

  def adapters(keyword: String) = List(new YCombinator(5), new Rabotaua(5, keyword), new Stackoverflow(5, keyword))

  def all(keyword: String): Future[Set[CrawlCandidate]] =
    Future.fold(adapters(keyword).map(_.candidates))(List.empty[CrawlCandidate])(_ ::: _) map { _.toSet }

  def allChecked(keyword: String, scanner: Scanner): Future[Set[Future[CrawlResult]]] =
    Future.fold(adapters(keyword).map(_.checked(scanner)))(Set.empty[Future[CrawlResult]])(_ union _)
}

trait Adapter {
  import Adapter.ec

  protected val startingPoint: URL
  protected val maxPages: Int

  def checked(scanner: Scanner): Future[Set[Future[CrawlResult]]] = {
    candidates map { _.toSet } map { scanner.scan }
  }

  def candidates: Future[List[CrawlCandidate]] = getCandidateLinks() map { _._1 }

  private def getCandidateLinks(from: URL = startingPoint, currentPage: Int = 1): Future[(List[CrawlCandidate], Int)] = {
    def loop(url: URL, candidates: List[CrawlCandidate]) =
      Future.fold(List(getCandidateLinks(url, currentPage + 1)))((candidates, currentPage)) {
        case (l1, l2) => {
          (l1._1 ::: l2._1, currentPage)
        }
      }

    extractLinksFrom(from) flatMap {
      case (candidates: List[CrawlCandidate], nextPage: Option[URL]) =>
        nextPage
          .flatMap { u: URL => if (currentPage <= maxPages) Some(loop(u, candidates)) else None }
          .getOrElse { Future successful ((candidates, currentPage)) }
    }
  }

  private def extractLinksFrom(location: URL): Future[(List[CrawlCandidate], Option[URL])] = Future {
    Try(
      getBrowserInstance.get(location.toString())
    )
      .map { doc =>
        val results = doExtractLinks(doc) map { CrawlCandidate(this.getClass.getSimpleName, location, _) }
        (results, nextPage(doc))
      }.getOrElse { (List.empty, None) }
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
