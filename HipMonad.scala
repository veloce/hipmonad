import scalaz.{ Success, Failure, NonEmptyList }
import ornicar.scalalib.{ Validation, Common }
import dispatch._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scala_tools.time.Imports._
import net.liftweb.json.JsonParser
import net.liftweb.json.DefaultFormats
import net.liftweb.json._

object HipMonad extends Validation {

  case class Tweet(
      id: Long,
      created_at: String,
      from_user: String,
      profile_image_url: String,
      text: String) {

    private val fmt = DateTimeFormat forPattern "EEE, dd MMM yyyy HH:mm:ss Z"

    def createdAt = fmt.parseDateTime(created_at)
    def fromUser = from_user
    def profileImgUrl = profile_image_url
    override def toString = "%s: id: %s - %s".format(createdAt, id, fromUser)
  }

  def withHttp[A](f: Http ⇒ Promise[A]): Promise[A] = {
    val http = new Http
    val promise = f(http)
    promise onComplete (_ ⇒ http.shutdown)
    promise
  }

  def post(tweet: Tweet): Promise[String] = {

    def formatMessage(from: String, msg: String, id: Long, imgUrl: String): String = {

      val urlregex = """(https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])""".r
      val hashregex = """\#(\w+)""".r
      val atregex = """@(\w+)""".r

      def repUrl(txt: String) = urlregex.replaceAllIn(txt, """<a href="$1" target="_blank">$1</a>""")
      def repHash(txt: String) = hashregex.replaceAllIn(txt, """<a href="https://twitter.com/search?q=$1">#$1</a>""")
      def repAt(txt: String) = atregex.replaceAllIn(txt, """<a href="https://twitter.com/$1">@$1</a>""")

      val repAll = repHash _ compose repAt _ compose repUrl _

      """
      <img alt="%1$s" src="%4$s">
      &nbsp;<a href="https://twitter.com/%1$s">@%1$s</a>
      <br />
      %2$s
      <br />
      <a href="https://twitter.com/%1$s/status/%3$d">Details</a>
      """ format(from, repAll(msg), id, imgUrl)
    }

    val hipPost = url("http://api.hipchat.com/v1/rooms/message")
      .addQueryParameter("auth_token", config.token)
      .addParameter("from", config.hipchat_from)
      .addParameter("room_id", config.room_id)
      .addParameter("color", config.color)
      .addParameter("message", formatMessage(tweet.fromUser, tweet.text, tweet.id, tweet.profileImgUrl))
      .POST

    withHttp(_(hipPost OK as.String))
  }

  def postTweets(list: List[Tweet]): Promise[Option[Long]] = {
    val ids = for (tweet ← list) yield {
      post(tweet) onComplete println
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
    val fmt = DateTimeFormat forPattern "YYYY-MM-dd"

    val search = url("http://search.twitter.com/search.json")
      .addQueryParameter("q", "%s since:%s".format(config.search_pattern, fmt print DateTime.now))
      .addQueryParameter("result_type", "recent")
      .addQueryParameter("rpp", "100")
      .addQueryParameter("since_id", sinceId toString)

    val promise = withHttp(_(search OK as.String).option)
    promise map { rawOption ⇒
      for {
        raw ← rawOption toValid "Twitter api call returned an error"
        tweets ← parseJson(raw)
      } yield tweets filter (e ⇒
        e.createdAt > DateTime.now - config.twitter_check_interval.second - 30.second) take 2
    }
  }

  def main(args: Array[String]) {
    run(0)
  }

  def run(sinceId: Long) {
    var currentSinceId = sinceId

    while (true) {
      val promise = lastTweets(currentSinceId) flatMap { validList ⇒
        validList fold (
          errs ⇒ {
            printLnFailures(errs map (s ⇒ "%s: %s".format(DateTime.now, s)))
            Promise apply None
          },
          list ⇒ {
            postTweets(list)
          }
        )
      } onComplete {
        case Left(e)         ⇒ println(e)
        case Right(Some(id)) ⇒ currentSinceId = id
        case _               ⇒
      }
      promise()
      Thread.sleep(config.twitter_check_interval * 1000)
    }
  }
}
