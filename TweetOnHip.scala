import scalaz.{ Success, Failure, NonEmptyList }
import ornicar.scalalib.{ Validation, Common }
import dispatch._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scala_tools.time.Imports._
import net.liftweb.json.JsonParser
import net.liftweb.json.DefaultFormats
import net.liftweb.json._

object TweetOnHip extends Validation {

  case class Tweet(
    id: Int,
    created_at: DateTime,
    from_user: String,
    text: String)

  def withHttp[A](f: Http ⇒ Promise[A]): Promise[A] = {
    val http = new Http
    val promise = f(http)
    promise onComplete (_ ⇒ http.shutdown)
    promise
  }

  def post(from: String, message: String): Promise[Unit] = {
    val hipPost = url("http://api.hipchat.com/v1/rooms/message")
      .addQueryParameter("auth_token", config.token)
      .addParameter("from", "twitOnHip")
      .addParameter("room_id", config.room_id)
      .addParameter("message", "@%s: %s".format(from, message))
      .POST

    withHttp(_(hipPost OK as.String)) map (_ ⇒ Unit)
  }

  def postTweets(list: List[Tweet]): Promise[Option[Int]] = {
    val ids = for (tweet ← list) yield {
      post(tweet.from_user, tweet.text)
      tweet.id
    }

    Promise apply ids.lastOption
  }

  def parseJson(jsonString: String): Valid[List[Tweet]] = try {
    implicit val formats = DefaultFormats
    val json = JsonParser parse jsonString
    val resultsJson = json \ "results"
    Success(resultsJson.extract[List[Tweet]])
  }
  catch {
    case e ⇒ Failure(NonEmptyList("Error: " + e.toString))
  }

  def lastTweets(sinceId: Int): Promise[Valid[List[Tweet]]] = {
    val dateFormat = "YYYY-MM-dd"
    val dateFormatter = DateTimeFormat forPattern dateFormat

    val search = url("http://search.twitter.com/search.json")
      .addQueryParameter("q", "%s since:%s".format(config.search_pattern, dateFormatter print DateTime.now))
      .addQueryParameter("since_id", sinceId toString)

    val promise = withHttp(_(search OK as.String).option)
    promise map { rawOption ⇒
      for {
        raw ← rawOption toValid "Twitter api call returned an error"
        tweets ← parseJson(raw)
      } yield tweets filter (e ⇒ e.created_at > DateTime.now - 1.minute)
    }
  }

  def main(args: Array[String]) {
    run(0)
  }

  def run(sinceId: Int) {
    lastTweets(sinceId) flatMap { validList ⇒
      validList fold (
        errs ⇒ {
          printLnFailures(errs map (s ⇒ "%s: %s".format(DateTime.now, s)))
          Promise apply None
        },
        list ⇒ {
          println(list map (s ⇒ "%s: %s".format(DateTime.now, s)))
          postTweets(list)
        }
      )
    } onComplete { either ⇒
      Thread.sleep(60 * 1000)
      either match {
        case Left(e) ⇒ {
          println(e)
          run(sinceId)
        }
        case Right(optionId) ⇒ optionId fold (
          newId ⇒ run(newId),
          run(sinceId)
        )
      }
    }
  }
}
