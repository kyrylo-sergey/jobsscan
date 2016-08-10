import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorAttributes, ActorMaterializer, Supervision, scaladsl}
import java.net.URL
import scaladsl.{Flow, Sink, Source}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import org.scalameter._
import scala.language.postfixOps

object Timetest extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val standardConfig = config(
    Key.exec.minWarmupRuns -> 5,
    Key.exec.maxWarmupRuns -> 10,
    Key.exec.benchRuns -> 1,
    Key.verbose -> true
  ) withWarmer (new Warmer.Default)

  val keyword = "Scala"

  val streamtime = standardConfig measure {
    val decider: Supervision.Decider = {
      case _ => Supervision.Resume
    }

    val scanner = new SimpleScanner(keyword)

    val s = Main.candidatesSource(new YCombinator(5), new Rabotaua(1, keyword), new Stackoverflow(1, keyword))
    val a = s.mapAsync(100) { u =>
      Future {
        val res = if (scanner.isCandidateAcceptable(u)) {
          ("SuccessfulCandidate", u.toString())
        } else {
          ("FailedCandidate", u.toString())
        }

        //  println("working on " + u.toString() + "res: " + res.toString())
        res
      }
    } withAttributes (ActorAttributes.supervisionStrategy(decider))

    //    a runWith (Sink.onComplete { case _ => system.terminate() })
    val f = a runWith { Sink.seq }

    Await.result(f, 1 hour)
  }

  //----------

  val oldtime = standardConfig measure {
    val listOfScannedLinks = Await.result(Adapter.allChecked(keyword), 1 hour)

    def futureURLToJson(f: Future[URL]) = f
      .map { (u: URL) => ("SuccessfulCandidate", u.toString()) }
      .recover { case t: Throwable => ("FailedCandidate", t.toString()) }

    val res = listOfScannedLinks map futureURLToJson

    val resFuture: Set[(String, String)] = Await.result(Future.sequence(res), 1 hour)
  }

  println(s"Stream Time: $streamtime")
  println(s"Old Time: $oldtime")

  println(s"speedup: " + oldtime / streamtime)

  system.terminate()
}
