import scala.scalajs.js.JSApp
import org.scalajs.dom
import org.scalajs.dom.raw._
import scala.scalajs.js.Dynamic.global
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

    socket.onopen = (e: scala.scalajs.js.Any) => {
      global.console.log("Connected to WebSocket Server on " + WSServer)
    }

    socket.onerror = (e: dom.Event) => {
      global.console.log("Error occurred:", e)
    }

    socket.onmessage = (e: dom.MessageEvent) => {
      read[Tuple2[String, String]](e.data.toString()) match {
        case ("SuccessfulCandidate", url) => appendCandidate(links, url)
        case ("FailedCandidate", problem) => {}
        case _ => global.console.error("unknown message", e)
      }
    }

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
      }, false)
    });
  }
}
