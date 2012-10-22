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
      id: Long,
      created_at: String,
      from_user: String,
      text: String) {

    def createdAt = created_at
    def fromUser = from_user
    override def toString = "%s: id: %s - %s".format(createdAt, id, fromUser)
  }

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

  def postTweets(list: List[Tweet]): Promise[Option[Long]] = {
    val ids = for (tweet ← list) yield {
      post(tweet.fromUser, tweet.text)
      tweet.id
    }

    Promise apply ids.headOption
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

  def lastTweets(sinceId: Long): Promise[Valid[List[Tweet]]] = {
    val dateFormat = "YYYY-MM-dd"
    val dateFormatter = DateTimeFormat forPattern dateFormat

    val search = url("http://search.twitter.com/search.json")
      .addQueryParameter("q", "%s since:%s".format(config.search_pattern, dateFormatter print DateTime.now))
      .addQueryParameter("result_type", "recent")
      .addQueryParameter("rpp", "100")
      .addQueryParameter("since_id", sinceId toString)

    val promise = withHttp(_(search OK as.String).option)
    promise map { rawOption ⇒
      for {
        raw ← rawOption toValid "Twitter api call returned an error"
        tweets ← parseJson(raw)
      } yield tweets
    }
  }

  def main(args: Array[String]) {
    run(0)
  }

  def run(sinceId: Long) {
    lastTweets(sinceId) flatMap { validList ⇒
      validList fold (
        errs ⇒ {
          printLnFailures(errs map (s ⇒ "%s: %s".format(DateTime.now, s)))
          Promise apply None
        },
        list ⇒ {
          println("*----* %s *----*".format(DateTime.now))
          list foreach println
          postTweets(list)
        }
      )
    } onComplete { either ⇒
      Thread.sleep(config.twitter_check_interval * 1000)
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
