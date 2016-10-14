import scalatags.Text.all._

object Templates {

  def index(providers: List[(String, String)]) =
    html(
      head(
        title := "JobsScan",
        link(
          rel := "stylesheet",
          href := "https://cdnjs.cloudflare.com/ajax/libs/materialize/0.97.7/css/materialize.min.css"
        )
      ),
      body(
        div(
          cls := "container",
          div(
            cls := "row",
            div(cls := "col s9", id := "header",
              div(
                cls := "row",
                h1("JobsScan"), h6("a better way to find your dream job ~>")
              ))
          ),
          div(
            cls := "row",
            div(cls := "col s9", id := "content",
              div(
                cls := "row",
                div(cls := "col s6", input(id := "search-box", tpe := "text", name := "search")),
                div(cls := "col s3", a("Search", id := "search-btn", cls := "waves-effect waves-light btn"))
              ),
              div(id := "providers", cls := "row", for ((k, v) <- providers) yield {
                p(input(tpe := "checkbox", value := k, id := k), label(`for` := k, k))
              }),
              div(id := "progress", cls := "row"),
              div(id := "links", cls := "row")
            )
          )
        ),
        script(tpe := "text/javascript", src := "jobsscan-deps.js"),
        script(tpe := "text/javascript", src := "jobsscan.js"),
        script("new JobScaner().main();", tpe := "text/javascript")
      )
    )
}
