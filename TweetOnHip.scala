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

object TweetOnHip {

  val message = "bluk bluk!"

  val hipPost = url("http://api.hipchat.com/v1/rooms/message")
    .addQueryParameter("auth_token", token)
    .addParameter("from", "tweet on hip")
    .addParameter("room_id", room_id)
    .addParameter("message", message)
    .POST

  private val dateFormat = "YYYY-MM-dd"
  private val dateFormatter = DateTimeFormat forPattern dateFormat

  val twitGet = url("http://search.twitter.com/search.json")
    .addQueryParameter("q", "obama since:%s".format(dateFormatter print DateTime.now))

  def main(args: Array[String]) {
    implicit val formats = DefaultFormats
    val response = Http(twitGet OK as.String)
    for (raw <- response) {
      try {
        val json = JsonParser parse raw
        val resultsJson = json \ "results"
        val tweets = resultsJson.extract[List[Tweet]]
        println(tweets)
      } finally {
        Http.shutdown
      }
    }

    /* val promise = Http(hipPost OK as.String) */
    /* for (r <- promise) { */
    /*   println(r) */
    /*   Http.shutdown */
    /* } */
  }
}
