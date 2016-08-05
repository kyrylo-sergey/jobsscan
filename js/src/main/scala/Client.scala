import scala.scalajs.js.JSApp
import org.scalajs.dom
import dom.document

object Client extends JSApp {

  def appendPar(targetNode: dom.Node, text: String): Unit = {
    val parNode = document.createElement("p")
    val textNode = document.createTextNode(text)
    parNode.appendChild(textNode)
    targetNode.appendChild(parNode)
  }

  def main(): Unit = {
    appendPar(document.body, "Hello World")
    appendPar(document.body, SuccessfulCandidate("http://www.example.com").toString())
  }

}
