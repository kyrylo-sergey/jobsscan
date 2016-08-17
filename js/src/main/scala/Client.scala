import scala.scalajs.js.JSApp
import org.scalajs.jquery.JQueryEventObject
import org.scalajs.dom
import org.scalajs.dom.raw._
import org.scalajs.jquery.{jQuery => JQ}
import scala.scalajs.js.Dynamic.global
import scala.scalajs.js.timers._
import scala.scalajs.js.{Any}
import dom.document
import upickle.default._
import scala.concurrent.{Promise, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.collection.mutable

object Client extends JSApp {
  private final val WSServer = "ws://localhost:8080/ws-echo"

  def appendCandidate(targetNode: dom.Node, url: String): Unit = {
    val aNode = document.createElement("a")
    aNode.setAttribute("href", url)
    aNode.innerHTML = url
    aNode.setAttribute("target", "_blank")

    targetNode.appendChild(document.createElement("br"))
    targetNode.appendChild(aNode)
  }

  def main(): Unit = {
    val links = document.getElementById("links")
    var progress: Option[Progress] = None

    def bindConnectionEvents(conn: Connection) = {

      conn.open onSuccess { case _ => Progress.show }
      conn.close onComplete { case _ => Progress.remove }

      conn
        .onError { e: dom.Event => global.console.error("Error occured", e) }
        .onMessage("SuccessfulCandidate") { url =>
          appendCandidate(links, url)
          progress foreach { _.progress }
        }
        .onMessage("FailedCandidate") { _ => progress foreach { _.progress } }
        .onMessage("CandidatesCount") { countString =>
          progress = Some(new Progress(countString.toInt))
        }
    }

    JQ {
      JQ("#search-btn").on("click", { e: JQueryEventObject =>
        val conn = new Connection()
        bindConnectionEvents(conn)
        conn.send("StartSearch", document.getElementById("search-box") match {
          case elem: HTMLInputElement => elem.value
          case elem =>
            global.console.error(s"expected HTMLInputElement, got $elem")
            throw new RuntimeException()
        })
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
  private val onMessageCallbacks: mutable.Map[String, List[MessageHandler]] =
    mutable.Map.empty withDefault { _ => List.empty }
  private val onErrorCallbacks: mutable.ListBuffer[ErrorHandler] =
    mutable.ListBuffer.empty

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

  def onMessage(messageType: String)(h: MessageHandler) = {
    onMessageCallbacks.update(messageType, h :: onMessageCallbacks(messageType))
    this
  }

  def onError(h: ErrorHandler) = { onErrorCallbacks += h; this }

  def send(messageType: String, data: String) = socket foreach {
    case socket: WebSocket => socket.send(write((messageType, data)))

  }
}

object Progress {

  private def jqnode = JQ("#progress")

  def isShown = jqnode.length == 1
  def isDeterminate = jqnode.find("div.determinate").length == 1
  def isIndeterminate = !isDeterminate

  def show = jqnode.append("""
    <div class="progress">
      <div class="indeterminate"></div>
    </div>
    """.stripMargin)

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

  def progress = {
    count += 1

    Progress.progress(count.toDouble / totalMessages.toDouble * 100)

    if (count == totalMessages) setTimeout(3000) { Progress.remove }
  }
}
