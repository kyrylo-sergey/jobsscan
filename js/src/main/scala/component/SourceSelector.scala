package component

import japgolly.scalajs.react.{FunctionalComponent, Callback, ReactEventI}
import japgolly.scalajs.react.vdom.prefix_<^._

object SourceSelector {

  case class Props(
    providers: Map[String, String],
    changeCallback: ReactEventI => Callback,
    selectedProviders: Set[String],
    disabled: Boolean
  )

  def apply() = FunctionalComponent[Props] { p =>
    <.div(^.id := "providers", ^.cls := "row", for ((k, v) <- p.providers) yield {
      <.div(
        ^.key := s"provider-$k",
        <.input(
          ^.tpe := "checkbox",
          ^.value := k,
          ^.id := k,
          ^.onChange ==> p.changeCallback,
          ^.checked := p.selectedProviders contains k,
          ^.disabled := p.disabled
        ),
        <.label(^.`for` := k, k),
        <.a(^.href := v, ^.target := "_blank", s" ($v)")
      )
    })
  }
}
