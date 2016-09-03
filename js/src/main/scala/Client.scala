import scala.scalajs.js.JSApp
import org.scalajs.jquery.JQueryEventObject
import org.scalajs.dom
import org.scalajs.dom.raw._
import org.scalajs.jquery.{jQuery => JQ, JQuery}
import scala.scalajs.js.Dynamic.global
import scala.scalajs.js.timers._
import scala.scalajs.js.{Any}
import dom.document
import upickle.default._
import scala.concurrent.{Promise, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.collection.mutable
import scalatags.JsDom.all._

import proto._

object Client extends JSApp {
  private final val WSServer = s"ws://${document.location.host}/ws-echo"

  def appendCandidate(targetNode: JQuery, cs: CrawlSuccessful): Unit =
    targetNode.find("table tbody") append tr(
      td(a(cs.source, href := cs.initialURL, target := "_blank")),
      td(a(cs.title, href := cs.targetURL, target := "_blank")),
      td(cs.foundText)
    ).render

  def main(): Unit = {
    val links = JQ("#links")
    val btn = JQ("#search-btn")

    def bindConnectionEvents(conn: Connection) = {
      val progress = conn.progressInfo.map(new Progress(_))

      progress.flatMap(_.complete) onSuccess {
        case _ =>
          conn.doClose
          btn.removeClass("disabled")
      }

      conn.open onSuccess { case _ => Progress.show }

      conn
        .onError { e: dom.Event => global.console.error("Error occured", e) }
        .onMessage(Msg.CRAWL_SUCCESSFUL) { case cs: CrawlSuccessful =>
          appendCandidate(links, cs)
          progress foreach { _.progress }
        }
        .onMessage(Msg.CRAWL_UNSUCCESSFUL) { case cu: CrawlUnsuccessful =>
          progress foreach { _.progress }
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
          btn.addClass("disabled")
          val conn = new Connection(WSServer)
          bindConnectionEvents(conn)
          refreshResultsTable
          val term = document.getElementById("search-box") match {
            case elem: HTMLInputElement => elem.value
            case elem =>
              global.console.error(s"expected HTMLInputElement, got $elem")
              throw new RuntimeException()
          }
          conn.send(Msg.START_SEARCH, write(StartSearch(term)))
        }
      })
    }
  }
}

class Connection(private val url: String) {

  private type MessageHandler = PartialFunction[ProtoMessage, Unit]
  private type ErrorHandler = dom.Event => Unit

  private val openPromise = Promise[Any]()
  private val closedPromise = Promise[dom.Event]()
  private val socketPromise = Promise[WebSocket]()
  private val progressPromise = Promise[Int]
  private val onMessageCallbacks: mutable.Map[String, List[MessageHandler]] =
    mutable.Map.empty withDefault { _ => List.empty }
  private val onErrorCallbacks: mutable.ListBuffer[ErrorHandler] =
    mutable.ListBuffer.empty

  onMessage(Msg.CANDIDATES_COUNT) { case CandidatesCount(c) => progressPromise success c }

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

  def onMessage(messageType: String)(h: MessageHandler) = {
    onMessageCallbacks.update(messageType, h :: onMessageCallbacks(messageType))
    this
  }

  def onError(h: ErrorHandler) = { onErrorCallbacks += h; this }

  def send(messageType: String, data: String) = socket foreach {
    case socket: WebSocket => socket.send(write((messageType, data)))
  }

  def doClose = socket onSuccess { case ws => ws.close() }
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
