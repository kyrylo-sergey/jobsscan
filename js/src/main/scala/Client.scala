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

object Client extends JSApp {
  private final val WSServer = s"ws://${document.location.host}/ws-echo"

  def appendCandidate(targetNode: JQuery, url: String): Unit =
    targetNode append p(a(url, href := url, target := "_blank")).render

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
        .onMessage("SuccessfulCandidate") { url =>
          appendCandidate(links, url)
          progress foreach { _.progress }
        }
        .onMessage("FailedCandidate") { p =>
          progress foreach { _.progress }
        }
    }

    JQ {
      btn.on("click", { e: JQueryEventObject =>
        if (!btn.hasClass("disabled")) {
          btn.addClass("disabled")
          val conn = new Connection()
          bindConnectionEvents(conn)
          links.html("")
          conn.send("StartSearch", document.getElementById("search-box") match {
            case elem: HTMLInputElement => elem.value
            case elem =>
              global.console.error(s"expected HTMLInputElement, got $elem")
              throw new RuntimeException()
          })
        }
      })
    }
  }
}

class Connection(private val url: String = "ws://localhost:8080/ws-echo") {

  private type MessageHandler = String => Unit
  private type ErrorHandler = dom.Event => Unit

  private val openPromise = Promise[Any]()
  private val closedPromise = Promise[dom.Event]()
  private val socketPromise = Promise[WebSocket]()
  private val progressPromise = Promise[Int]
  private val onMessageCallbacks: mutable.Map[String, List[MessageHandler]] =
    mutable.Map.empty withDefault { _ => List.empty }
  private val onErrorCallbacks: mutable.ListBuffer[ErrorHandler] =
    mutable.ListBuffer.empty

  onMessage("CandidatesCount") { progressPromise success _.toInt }

  private def connect: WebSocket = {
    val socket = new WebSocket(url)
    socketPromise.success(socket)
    socket.onopen = (e: Any) => openPromise.success(e)
    socket.onclose = (e: dom.Event) => closedPromise.success(e)
    socket.onmessage = (e: dom.MessageEvent) => {
      val (messageType, content) = read[(String, String)](e.data.toString())
      onMessageCallbacks(messageType) foreach { _(content) }
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
