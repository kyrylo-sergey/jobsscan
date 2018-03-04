package component

import japgolly.scalajs.react.FunctionalComponent
import japgolly.scalajs.react.vdom.HtmlStyles
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.js.Dynamic.global

object Progress extends HtmlStyles {

  case class Props(progress: Option[Double])

  def apply() = FunctionalComponent[Props] { p =>

      global.console.log(p.progress map(count => s"${count}%") getOrElse "0%" toString())

      <.div(
      ^.cls := "progress",
      <.div(
        ^.cls := { if (p.progress.isDefined) "determinate" else "indeterminate" },
        ^.width := p.progress map(count => s"${count}%") getOrElse "0%"
      )
    )
  }
}
