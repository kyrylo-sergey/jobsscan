import scala.scalajs.js.JSApp
import org.scalajs.dom
import org.scalajs.dom.raw._
import scala.scalajs.js.Dynamic.global
import dom.document
import proto._

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
    appendPar(document.body, SuccessfulCandidate("http://www.example.com").toString())

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
      appendPar(document.body, e.data.toString())
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
