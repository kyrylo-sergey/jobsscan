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
            div(cls := "col s9", id := "content")
          )
        ),
        script(tpe := "text/javascript", src := "jobsscan-deps.js"),
        script(tpe := "text/javascript", src := "jobsscan.js"),
        script("new JobScaner().main();", tpe := "text/javascript")
      )
    )
}
