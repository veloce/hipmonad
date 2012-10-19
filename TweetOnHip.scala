import scalaz.{ Success , Failure, NonEmptyList }
import ornicar.scalalib.{Validation , Common }
import dispatch._
import Config._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import net.liftweb.json.JsonParser
import net.liftweb.json.DefaultFormats
import net.liftweb.json._

case class Tweet(
  created_at: DateTime,
  from_user: String,
  text: String
)

object TweetOnHip extends Validation {

  def withHttp[A](f: Http => Promise[A]): Promise[A] = {
    val http = new Http
    val promise = f(http)
    promise onComplete (_ => http.shutdown)
    promise
  }

  def post(from: String, message: String): Promise[Unit] = {
    val hipPost = url("http://api.hipchat.com/v1/rooms/message")
      .addQueryParameter("auth_token", token)
      .addParameter("from", "twig on hip")
      .addParameter("room_id", room_id)
      .addParameter("message", "@%s: %s".format(from, message))
      .POST

      withHttp(_(hipPost OK as.String)) map (_ => Unit)
  }

  def postTweets(list: List[Tweet]): Promise[Unit] = {
    val listP = for (tweet <- list)
      yield post(tweet.from_user, tweet.text)

    Promise all listP map (_ => Unit)
  }

  def parseJson(jsonString: String): Valid[List[Tweet]] = try {
      implicit val formats = DefaultFormats
      val json = JsonParser parse jsonString
      val resultsJson = json \ "results"
      Success(resultsJson.extract[List[Tweet]])
    } catch {
      case e => Failure(NonEmptyList("Error: " + e.toString))
    }

  def tweetsOfTheDay: Promise[Valid[List[Tweet]]] = {
    val dateFormat = "YYYY-MM-dd"
    val dateFormatter = DateTimeFormat forPattern dateFormat

    val search = url("http://search.twitter.com/search.json")
      .addQueryParameter("q", "@jirafe since:%s".format(dateFormatter print DateTime.now))

    val promise = withHttp(_(search OK as.String).option)
    promise map { rawOption =>
      for {
        raw <- rawOption toValid "Network error"
        tweets <- parseJson(raw)
      } yield tweets
    }
  }

  def main(args: Array[String]) {
    for {
      validList <- tweetsOfTheDay
      p <- validList fold (
        errs => {
          printLnFailures(errs)
          Promise()
        },
        list => postTweets(list take 5)
      )
    } {
      validList foreach println
    }
  }
}
