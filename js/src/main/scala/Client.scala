import scala.scalajs.js.JSApp
import org.scalajs.dom
import org.scalajs.dom.raw._
import scala.scalajs.js.Dynamic.global
import dom.document

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
      global.console.log("Connected to WebSocket Server on " ++ WSServer)
      socket.send("search")
    }

    socket.onerror = (e: dom.ErrorEvent) => {
      global.console.log("Error: " ++ e.toString())
    }

    socket.onmessage = (e: dom.MessageEvent) => {
      global.console.log(e)
    }
  }
}
