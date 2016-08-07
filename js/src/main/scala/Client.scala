import scala.scalajs.js.JSApp
import org.scalajs.dom
import org.scalajs.dom.raw._
import scala.scalajs.js.Dynamic.global
import dom.document
import upickle.default._

object Client extends JSApp {
  private final val WSServer = "ws://localhost:8080/ws-echo"

  def appendPar(targetNode: dom.Node, text: String): Unit = {
    val parNode = document.createElement("p")
    val textNode = document.createTextNode(text)
    parNode.appendChild(textNode)
    targetNode.appendChild(parNode)
  }

  def main(): Unit = {
    appendPar(document.body, "Hello World")

    val socket = new WebSocket(WSServer)

    socket.onopen = (e: scala.scalajs.js.Any) => {
      global.console.log("Connected to WebSocket Server on " + WSServer)
      socket.send("search")
    }

    socket.onerror = (e: dom.Event) => {
      global.console.log("Error occurred:", e)
    }

    socket.onmessage = (e: dom.MessageEvent) => {
      global.console.log(e)
      read[Tuple2[String, String]](e.data.toString()) match {
        case ("SuccessfulCandidate", url) => appendPar(document.body, url.toString())
        case _ => {}
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
        global.console.log(searchBox.value)
      }, false)
    });
  }
}
