package component

import japgolly.scalajs.react.{FunctionalComponent}
import japgolly.scalajs.react.vdom.prefix_<^._
import proto.CrawlSuccessful

object ResultTable {

  case class Props(results: Seq[CrawlSuccessful])

  def apply() = FunctionalComponent[Props] { p =>
    <.table(
      ^.cls := "responsive-table",
      <.thead(
        <.tr(<.th("Found At"), <.th("Link"), <.th("Found Text"))
      ),
      <.tbody(for (cs <- p.results) yield {
        <.tr(
          <.td(<.a(cs.source, ^.href := cs.initialURL, ^.target := "_blank")),
          <.td(<.a(cs.title, ^.href := cs.targetURL, ^.target := "_blank")),
          <.td(cs.foundText)
        )
      })
    )
  }
}
