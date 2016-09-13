import scala.scalajs.js.JSApp
import org.scalajs.jquery.JQueryEventObject
import org.scalajs.dom
import org.scalajs.dom.raw._
import org.scalajs.jquery.{jQuery => JQ, JQuery}
import scala.scalajs.js.Dynamic.global
import scala.scalajs.js.timers._
import scala.scalajs.js
import org.scalajs.dom
import dom.document
import upickle.default._
import scala.concurrent.{Promise, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.collection.mutable
import scalatags.JsDom.all._
import scala.concurrent.duration._
import scala.language.postfixOps

import proto._

object Client extends JSApp {
  private final val WSServer = s"ws://${document.location.host}/ws-echo"

  def appendCandidate(targetNode: JQuery, cs: CrawlSuccessful): Unit =
    targetNode.find("table tbody") append tr(
      td(a(cs.source, href := cs.initialURL, target := "_blank")),
      td(a(cs.title, href := cs.targetURL, target := "_blank")),
      td(cs.foundText)
    ).render

  implicit class MaterializeExt(val m: Materialize.type) extends AnyVal {
      def toast(msg: String, dur: Duration): Unit = m.toast(msg, dur.toMillis)
  }

  def main(): Unit = {
    val links = JQ("#links")
    val btn = JQ("#search-btn")
    val providers = JQ("#providers input")
    var connection: Option[Connection] = None

    def bindConnectionEvents(conn: Connection) = {
      val progress = conn.progressInfo.map(new Progress(_))

      progress.flatMap(_.complete) onSuccess { case _ => conn.doClose }

      conn.close onSuccess {
        case _ =>
          Progress.remove
          btn.removeClass("red")
          btn.text("Search")
          providers.attr("disabled", false)
      }

      conn.open onSuccess {
        case _ =>
          Progress.show
          providers.attr("disabled", true)
      }

      conn.searchFinished onSuccess {
        case count => if (count == 0) links.html("Nothing was found")
      }

      conn
        .onError { e: dom.Event => global.console.error("Error occured", e) }
        .onMessage(Msg.CRAWL_SUCCESSFUL) {
          case cs: CrawlSuccessful =>
            appendCandidate(links, cs)
            progress foreach { _.progress }
        }
        .onMessage(Msg.CRAWL_UNSUCCESSFUL) {
          case cu: CrawlUnsuccessful =>
            progress foreach { _.progress }
      }

      val messageFuture = for {
         total <- conn.progressInfo
         found <- conn.searchFinished
      } yield s"Found $found job postings from $total scanned job postings"

      messageFuture onSuccess { case msg =>
          Materialize.toast(msg, 6 seconds)
      }
    }

    def refreshResultsTable = {
      links.html("")

      val t = table(thead(
        tr(th("Found At"), th("Link"), th("Found Text"))
      ), tbody(), `class` := "responsive-table").render

      links append t
    }

    JQ {
      btn.on("click", { e: JQueryEventObject =>
        if (!btn.hasClass("disabled")) {
          if (btn.hasClass("red")) {
            btn.addClass("disabled")
            btn.text("Stopping")
            Materialize.toast("Stopping will take a few seconds", 3 seconds)
            connection.get.doClose onComplete {
              case _ =>
                btn.removeClass("red")
                btn.removeClass("disabled")
                btn.text("Search")
                Materialize.toast("Stoped", 3 seconds)
            }
          } else {
            val idents: Seq[String] = providers
              .filter { el: dom.Element => JQ(el).prop("checked").asInstanceOf[Boolean] }
              .map { el: dom.Element => JQ(el).value() }
              .asInstanceOf[js.Array[String]]

            val term = document.getElementById("search-box") match {
              case elem: HTMLInputElement => elem.value
              case elem =>
                global.console.error(s"expected HTMLInputElement, got $elem")
                throw new RuntimeException()
            }
            val keywordEntered = !term.trim.isEmpty()
            val atLeastOneSourceSelected = idents.size > 0

            if (keywordEntered && atLeastOneSourceSelected) {
              btn.addClass("red")
              btn.text("Stop")
              val conn = new Connection(WSServer)
              connection = Some(conn)
              bindConnectionEvents(conn)
              refreshResultsTable
              Materialize.toast(s"Started a search for $term at ${idents.mkString(", ")}", 6 seconds)
              conn.send(Msg.START_SEARCH, write(StartSearch(term, idents)))
            } else {
              if (!keywordEntered) Materialize.toast(s"Please enter a keyword", 3 seconds)
              if (!atLeastOneSourceSelected) Materialize.toast(s"Please select some of the job sources", 3 seconds)
            }
          }
        }
      })
    }
  }
}


@js.native
object Materialize extends js.Object {
  def toast(msg: String, millisDuration: Long): Unit = js.native
}

class Connection(private val url: String) {

  private type MessageHandler = PartialFunction[ProtoMessage, Unit]
  private type ErrorHandler = dom.Event => Unit

  private val openPromise = Promise[Any]()
  private val closedPromise = Promise[dom.Event]()
  private val socketPromise = Promise[WebSocket]()
  private val progressPromise = Promise[Int]
  private val searchFinishedPromise = Promise[Int]
  private val onMessageCallbacks: mutable.Map[String, List[MessageHandler]] =
    mutable.Map.empty withDefault { _ => List.empty }
  private val onErrorCallbacks: mutable.ListBuffer[ErrorHandler] =
    mutable.ListBuffer.empty

  onMessage(Msg.CANDIDATES_COUNT) { case CandidatesCount(c) => progressPromise success c }
  onMessage(Msg.SEARCH_FINISHED) { case SearchFinished(c) => searchFinishedPromise success c}

  private def connect: WebSocket = {
    val socket = new WebSocket(url)
    socketPromise.success(socket)
    socket.onopen = (e: Any) => openPromise.success(e)
    socket.onclose = (e: dom.Event) => closedPromise.success(e)
    socket.onmessage = (e: dom.MessageEvent) => {
      val (messageType, msgBody) = read[(String, String)](e.data.toString())
      //TODO: simplify this after https://issues.scala-lang.org/browse/SI-7046 is fixed
      val messageInstance: ProtoMessage = messageType match {
        case Msg.CANDIDATES_COUNT => read[CandidatesCount](msgBody)
        case Msg.CRAWL_SUCCESSFUL => read[CrawlSuccessful](msgBody)
        case Msg.CRAWL_UNSUCCESSFUL => read[CrawlUnsuccessful](msgBody)
        case Msg.SEARCH_FINISHED => read[SearchFinished](msgBody)
      }

      onMessageCallbacks(messageType) foreach { _(messageInstance) }
    }
    socket.onerror = (e: dom.Event) => onErrorCallbacks.toList foreach { _(e) }

    socket
  }

  private def socket: Future[WebSocket] = {
    if (!socketPromise.isCompleted) connect
    // there is no point of having socket instance
    // if it's connection is not yet opened
    open flatMap { _ => socketPromise.future }
  }

  def open = openPromise.future

  def close = closedPromise.future

  def progressInfo = progressPromise.future

  def searchFinished = searchFinishedPromise.future

  def onMessage(messageType: String)(h: MessageHandler) = {
    onMessageCallbacks.update(messageType, h :: onMessageCallbacks(messageType))
    this
  }

  def onError(h: ErrorHandler) = { onErrorCallbacks += h; this }

  def send(messageType: String, data: String) = socket foreach {
    case socket: WebSocket => socket.send(write((messageType, data)))
  }

  def doClose = {
    socket foreach { _.close() }
    close
  }
}

object Progress {

  private def jqnode = JQ("#progress")

  def isShown = jqnode.find(".progress").length == 1
  def isDeterminate = jqnode.find("div.determinate").length == 1
  def isIndeterminate = !isDeterminate

  def show = if (!isShown) {
    jqnode.append("""
      <div class="progress">
        <div class="indeterminate"></div>
      </div>
      """.stripMargin)
  }

  def progress(amount: Int): Unit = {
    assert(amount <= 100)

    val progress = jqnode.find(".progress").children().first

    if (isShown) {
      if (isIndeterminate) {
        progress.removeClass("indeterminate").addClass("determinate")
      }

      progress.attr("style", s"width: $amount%")
    }
  }

  def progress(amount: Double): Unit = progress(amount.toInt)

  def remove = if (isShown) jqnode.find(".progress").remove()
}

class Progress(val totalMessages: Int) {
  private var count = 0
  private val completePromise = Promise[Unit]()

  def progress = {
    count += 1

    Progress.progress(count.toDouble / totalMessages.toDouble * 100)

    if (count == totalMessages) {
      setTimeout(1000) {
        Progress.remove
        completePromise.success(Unit)
      }
    }
  }

  def complete = completePromise.future
}
