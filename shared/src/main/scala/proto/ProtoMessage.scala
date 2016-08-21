package proto

sealed trait ProtoMessage

case class NotSupported(op: String) extends ProtoMessage

// [client ~> server] messages
case class StartSearch(term: String) extends ProtoMessage

// [client <~ server] messages
case class CandidatesCount(count: Int) extends ProtoMessage
sealed abstract class CrawlResult(val source: String, val initialURL: String) extends ProtoMessage
case class CrawlSuccessful(override val source: String, override val initialURL: String, targetURL: String, foundText: String)
    extends CrawlResult(source, initialURL)
case class CrawlUnsuccessful(override val source: String, override val initialURL: String, targetURL: String, reason: String)
    extends CrawlResult(source, initialURL)

object Msg {
  final val CANDIDATES_COUNT = "CandidatesCount"
  final val CRAWL_SUCCESSFUL = "CrawlSuccessful"
  final val CRAWL_UNSUCCESSFUL = "CrawlUnsuccessful"
  final val START_SEARCH = "StartSearch"
}
