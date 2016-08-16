import scala.scalajs.js.JSApp
import org.scalajs.dom
import org.scalajs.dom.raw._
import org.scalajs.jquery.{jQuery => JQ}
import scala.scalajs.js.Dynamic.global
import scala.scalajs.js.timers._
import dom.document
import upickle.default._

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
    val socket = new WebSocket(WSServer)
    val links = document.getElementById("links")
    var progress: Option[Progress] = None

    socket.onopen = (e: scala.scalajs.js.Any) => {
      global.console.log("Connected to WebSocket Server on " + WSServer)
    }

    socket.onerror = (e: dom.Event) => {
      global.console.log("Error occurred:", e)
    }

    socket.onmessage = (e: dom.MessageEvent) =>
      read[Tuple2[String, String]](e.data.toString()) match {
        case ("SuccessfulCandidate", url) =>
          progress foreach { _.progress }
          appendCandidate(links, url)
        case ("FailedCandidate", problem) => progress foreach { _.progress }
        case ("CandidatesCount", countString) => progress = Some(new Progress(countString.toInt))
        case _ => global.console.error("unknown message", e)
      }

    socket.onclose = (e: dom.Event) => Progress.remove

    document.addEventListener("DOMContentLoaded", { (e: dom.Event) =>
      val searchBox = document.getElementById("search-box") match {
        case (elem: HTMLInputElement) =>
          elem
        case elem =>
          val err = s"expected HTMLInputElement, got $elem"
          global.console.error(err)
          throw new RuntimeException(err)
      }
      val searchBtn = document.getElementById("search-btn")

      searchBtn.addEventListener("click", { (mouseEvent: dom.Event) =>
        socket.send(write(("StartSearch", searchBox.value)))
        links.innerHTML = ""
        Progress.show
      }, false)
    });
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
