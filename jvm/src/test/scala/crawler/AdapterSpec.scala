package crawler

import java.net.URL

import scala.concurrent._
import scala.concurrent.duration._

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito

import net.ruippeixotog.scalascraper.model.Document
import net.ruippeixotog.scalascraper.browser.JsoupBrowser

class AdapterSpec extends Specification with Mockito {

  val start = new URL("http://example.org/jobs")
  val page1 = List(new URL("http://example.org"), new URL("http://example.org/some"))
  val page2 = List(new URL("http://example.org/other"), new URL("http://example.org/thing"))
  val url1 = new URL("http://exapmle.org/link/tp/page1")
  val browserMock = mock[JsoupBrowser]

  browserMock.get(start.toString()) returns mock[Document]
  browserMock.get(url1.toString()) returns mock[Document]

  class MyAdapter(override val maxPages: Int = 5) extends Adapter {

    override protected val startingPoint = start

    var pageCall = 0

    override protected def doExtractLinks(doc: Document): List[URL] = {
      pageCall += 1
      pageCall match {
        case 1 => page1
        case 2 => page2
        case _ => page2
      }
    }

    protected def nextPage(doc: Document): Option[URL] = pageCall match {
      case 0 => Some(url1)
      case 1 => Some(url1)
      case _ => None
    }

    override protected def getBrowserInstance = browserMock
  }

  "adapter instance should" >> {
    "must iterate over pages" in {
      val a = new MyAdapter()
      Await.result(a candidates, 1 second) must be_==(page1 ++ page2)
    }

    "should not iterate for more pages than parametrized" in {
      val a = new MyAdapter(1)
      Await.result(a candidates, 1 second) must be_==(page1)
    }

  }
}
