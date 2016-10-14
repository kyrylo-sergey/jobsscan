import scala.util.{Success, Failure}

import org.scalajs.dom
import org.scalajs.dom.raw._
import org.scalajs.dom.{WebSocket}
import scala.scalajs.js.JSApp
import scala.scalajs.js.Dynamic.global
import dom.document
import upickle.default._
import japgolly.scalajs.react.{ReactComponentB, ReactDOM, BackendScope, Callback, ReactEventI, CallbackTo}
import japgolly.scalajs.react.FunctionalComponent._
import japgolly.scalajs.react.vdom.prefix_<^._

import proto._
import shared.Domain
import component.{Button, Progress, SourceSelector, ResultTable}

object JobScaner extends JSApp {

  private final val WSServer = s"ws://${document.location.host}/ws-echo"

  object Main {

    case class State(
      ws: Option[WebSocket],
      working: Boolean,
      stopping: Boolean,
      successfullCandidates: Vector[CrawlSuccessful],
      failedCandidates: Vector[CrawlUnsuccessful],
      searchTerm: String,
      candidatesToReceive: Int,
      selectedSources: Set[String]
    ) {
      def startSearch = copy(working = true, successfullCandidates = Vector(),
        failedCandidates = Vector(), candidatesToReceive = 0)

      def stopSearch = copy(working = false, stopping = true)

      def totalReceived = successfullCandidates.length + failedCandidates.length
    }

    private final val initialState = State(None, false, false, Vector(), Vector(), "", 0, Set(Domain.providers.keys.toSeq: _*))

    val component = ReactComponentB[Unit]("MainComponent")
      .initialState(initialState)
      .renderBackend[Main.Backend]
      .componentDidMount(_.backend.start)
      .componentWillUnmount(_.backend.end)
      .build

    class Backend($: BackendScope[Unit, State]) {
      def render(s: State) = {
        val percentage = if (s.candidatesToReceive > 0) Some((s.totalReceived.toDouble / s.candidatesToReceive) * 100) else None
        val buttonTitle = if (s.working) "Stop" else if (s.stopping) "Stopping..." else "Search"

        <.div(
          <.div(
            ^.cls := "row",
            <.div(
              ^.cls := "col s6",
              <.input(^.id := "search-box", ^.tpe := "text", ^.name := "search", ^.value := s.searchTerm, ^.onChange ==> onChange)
            ),
            <.div(
              ^.cls := "col s3",
              Button() apply
                Button.Props(buttonTitle, s.working, sendSearch(s))
            )
          ),
          <.div(
            ^.cls := "row",
            if (s.working) Progress() apply Progress.Props(percentage) else ""
          ),
          SourceSelector() apply
            SourceSelector.Props(Domain.providers, onSourceChange, s.selectedSources, s.working || s.stopping),
          ResultTable() apply ResultTable.Props(s.successfullCandidates)
        )
      }

      import japgolly.scalajs.react.Callback._

      val direct = $.accessDirect

      def onChange(e: ReactEventI): Callback = {
        val newSearchTerm = e.target.value
        $.modState(_.copy(searchTerm = newSearchTerm)) >> reconnectOpt
      }

      def onSourceChange(e: ReactEventI): Callback = {
        val id = e.target.id
        $.modState { s: State =>
          if (s.selectedSources contains id) s.copy(selectedSources = s.selectedSources - id)
          else s.copy(selectedSources = s.selectedSources + id)
        } >> reconnectOpt
      }

      def reconnectOpt(): Callback = $.state >>= { s: State => when(s.ws.isEmpty)(start) }

      def handleMessage(msg: ProtoMessage) = msg match {
        case CandidatesCount(count) => direct.modState(_ copy (candidatesToReceive = count))
        case SearchFinished(succCount) => direct.modState(_ copy (working = false))
        case cs @ CrawlSuccessful(_, _, _, _, _) =>
          direct.modState(s => s.copy(successfullCandidates = s.successfullCandidates :+ cs))
        case cu @ CrawlUnsuccessful(_, _, _, _) =>
          direct.modState(s => s.copy(failedCandidates = s.failedCandidates :+ cu))
      }

      def sendSearch(s: State): Option[Callback] =
        for (conn <- s.ws if s.searchTerm.nonEmpty && s.selectedSources.nonEmpty) yield {
          if (s.working) $.modState(_.stopSearch) >> end
          else $.modState(_.startSearch) >> sendSearch(conn, s.searchTerm, s.selectedSources)
        }

      def sendSearch(conn: WebSocket, term: String, providers: Set[String]) = Callback apply {
        conn.send(write(Msg.START_SEARCH -> write(StartSearch(term, providers.toSeq))))
      }

      def start: Callback = {

        def connect = CallbackTo[WebSocket] {

          def onopen(e: Event): Unit = global.console.log("Connected.")

          def onmessage(e: MessageEvent): Unit = {
            val (messageType, msgBody) = read[(String, String)](e.data.toString())
            //TODO: simplify this after https://issues.scala-lang.org/browse/SI-7046 is fixed
            handleMessage {
              messageType match {
                case Msg.CANDIDATES_COUNT => read[CandidatesCount](msgBody)
                case Msg.CRAWL_SUCCESSFUL => read[CrawlSuccessful](msgBody)
                case Msg.CRAWL_UNSUCCESSFUL => read[CrawlUnsuccessful](msgBody)
                case Msg.SEARCH_FINISHED => read[SearchFinished](msgBody)
                case other => {
                  global.console.error(other)
                  NotSupported(other)
                }
              }
            }

          }

          def onerror(e: ErrorEvent): Unit = global.console.error(s"Error", e)

          def onclose(e: CloseEvent): Unit = {
            global.console.log(s"Closed", e)
            direct.modState(s => s.copy(working = false, stopping = false, ws = None))
          }

          val ws = new WebSocket(WSServer)
          ws.onopen = onopen _
          ws.onclose = onclose _
          ws.onmessage = onmessage _
          ws.onerror = onerror _
          ws
        }

        connect.attemptTry.flatMap {
          case Success(ws) => {
            global.console log s"Connecting to $WSServer..."
            $.modState(_.copy(ws = Some(ws), working = false))
          }
          case Failure(error) => {
            global.console error s"Error during connect to $WSServer"
            $.modState(_.copy(working = false))
          }
        }
      }

      def end: Callback = {
        def closeWebSocket = $.state.map(_.ws.foreach(_.close()))
        def clearWebSocket = $.modState(
          _.copy(
            ws = None,
            successfullCandidates = Vector(),
            failedCandidates = Vector()
          )
        )

        closeWebSocket >> clearWebSocket
      }

    }
  }

  def main(): Unit = ReactDOM.render(Main.component("MainComponent"), document.getElementById("content"))

}
