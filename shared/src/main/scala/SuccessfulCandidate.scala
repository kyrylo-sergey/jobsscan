object Proto {
  case class StartSearch(term: String)
}

case class SuccessfulCandidate(url: String)
