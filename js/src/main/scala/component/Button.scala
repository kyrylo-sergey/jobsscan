package component

import japgolly.scalajs.react.{FunctionalComponent, Callback}
import japgolly.scalajs.react.vdom.prefix_<^._

object Button {

  case class Props(title: String, working: Boolean, sendF: Option[Callback])

  def apply() = FunctionalComponent[Props] { p =>
    <.a(p.title, ^.id := "search-btn", ^.onClick -->? p.sendF, ^.classSet(
      "waves-effect" -> true,
      "waves-light btn" -> true,
      "red" -> p.working,
      "disabled" -> p.sendF.isEmpty
    ))
  }
}
