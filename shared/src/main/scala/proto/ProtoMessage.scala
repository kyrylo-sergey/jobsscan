package proto

sealed trait ProtoMessage

// [client ~> server] messages
case class StartSearch(term: String) extends ProtoMessage

// [client <~ server] messages
case class SearchFinished() extends ProtoMessage
case class SuccessfulCandidate(url: String) extends ProtoMessage
case class FailedCandidate(problem: String) extends ProtoMessage
