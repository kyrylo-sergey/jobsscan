package crawler

import java.net.URL
import java.util.regex.Pattern.quote

import scala._
import scala.concurrent._

import net.ruippeixotog.scalascraper.browser.JsoupBrowser

import proto._
import Adapter.ec

object Scanner {
  def all(keyword: String) = Set(
    new SimpleScanner(keyword)
  )

  def scan(keyword: String, candidates: Set[CrawlCandidate]) =
    all(keyword).map(_.scan(candidates)).fold(Set.empty)(_ ++ _)
}

trait Scanner {
  val searchCriteria: String

  def scan(candidates: Set[CrawlCandidate]): Set[Future[CrawlResult]] = candidates map { crawl =>
    Future {
      candidateAcceptable(crawl.targetURL) match {
        case Some((text, title)) => CrawlSuccessful(crawl.source, crawl.initialURL.toString(), crawl.targetURL.toString(), text, title)
        case None => CrawlUnsuccessful(crawl.source, crawl.initialURL.toString(), crawl.targetURL.toString(),
          s"${crawl.initialURL} doesn't contain $searchCriteria")
      }
    } recover {
      case t: Throwable =>
        CrawlUnsuccessful(crawl.source, crawl.initialURL.toString(), crawl.initialURL.toString(), t.toString)
    }
  }

  protected def candidateAcceptable(candidate: URL): Option[(String, String)]
}

class SimpleScanner(search: String) extends Scanner {
  override val searchCriteria = search

  override protected def candidateAcceptable(canidate: URL) = {
    val doc = JsoupBrowser().get(canidate.toString())
    val res = ("([^>]{0,50}" + quote(search) + ")(?=[^>]*(<|$))([^<]{0,50})").r
        .findFirstMatchIn(doc.body.innerHtml)
        .mkString

    if (res.isEmpty()) None else Some(res -> doc.title)
  }
}
