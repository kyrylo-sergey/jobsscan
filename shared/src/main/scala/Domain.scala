package shared

object Domain {
  final val YCombinator = "YCombinator"
  final val Rabotaua = "Rabotaua"
  final val StackOverflow = "StackOverflow"

  final val providers = Map(
    YCombinator -> "https://news.ycombinator.com/jobs",
    Rabotaua -> "http://rabota.ua",
    StackOverflow -> "http://stackoverflow.com/jobs"
  )
}
