import dispatch._
import Config._

object TwitOnHip {

  val message = "bluk bluk!"

  val request = url("http://api.hipchat.com/v1/rooms/message")
    .addQueryParameter("auth_token", token)
    .addParameter("from", "twit on hip")
    .addParameter("room_id", room_id)
    .addParameter("message", message)
    .POST

  def main(args: Array[String]) {
    val promise = Http(request OK as.String)
    for (r <- promise) {
      println(r)
      Http.shutdown
    }
  }
}
