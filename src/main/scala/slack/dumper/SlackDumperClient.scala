package slack.dumper

import java.security.MessageDigest

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.TextMessage
import org.apache.commons.codec.digest.DigestUtils
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}
import slack.api.BlockingSlackApiClient
import slack.rtm.{RtmState, SlackRtmClient, SlackRtmConnectionActor}

import scala.concurrent.duration.FiniteDuration

class SlackDumperClient(token: String, slackApiBaseUri: Uri, duration: FiniteDuration)(implicit arf: ActorSystem)
  extends SlackRtmClient(token, slackApiBaseUri, duration){
  override def createConnectionActor: ActorRef = {
    arf.actorOf(Props(new DumpingSlackRtmConnectionActor(apiClient, state)))
  }
}

class DumpingSlackRtmConnectionActor(apiClient: BlockingSlackApiClient, state: RtmState)
  extends SlackRtmConnectionActor(apiClient, state) {

  val SensitiveIdentifiers = "([A-Z][A-Z0-9]{5,15})".r
  val Url = "(http.*)".r
  val salt = scala.util.Random.nextString(10)

  val outputDirectory = java.nio.file.Files.createTempDirectory("slack-dumper").toFile

  println(s"Storing outputs to ${outputDirectory.getAbsolutePath}")

  override def onTextMessageReceive(message: TextMessage): Unit = {
    val payload = message.getStrictText
    val payloadJson = Json.parse(payload)

    val typ = (payloadJson \ "type").asOpt[String]
    val subtype = (payloadJson \ "subtype").asOpt[String].map(x => "-" + x).getOrElse("")

    val anonymized = anonymize(payloadJson)

    typ.foreach { someType =>
      val messageTypeIdentifier = someType + subtype
      println(s">>>>>>> $messageTypeIdentifier, $anonymized")
      val writer = new java.io.PrintWriter(new java.io.File(outputDirectory, s"${messageTypeIdentifier}.json"))
      writer.write(anonymized.toString)
      writer.close()
    }
  }

  def anonymize(playloadJson: JsValue): JsValue = {
    def transform(what: String): String = {
      val input = salt + what
      DigestUtils.md5Hex(input)
        .substring(0, what.length).toUpperCase
    }

    playloadJson match {
      case a: JsArray => Json.arr(a.value.map(anonymize))
      case o: JsObject =>
        JsObject(o.fields.map {
          case (fieldName, JsString(_)) if fieldName.contains("name") =>
            (fieldName, JsString("Some Name"))
          case (fieldName, JsString(SensitiveIdentifiers(s))) =>
            val transformed = s.charAt(0) + transform(s.substring(1))
            (fieldName, JsString(transformed))
          case (fieldName, JsString(Url(s))) =>
            (fieldName, JsString("https://example.com"))
          case (fieldName, o: JsObject) => (fieldName, anonymize(o))
          case (x, y) => (x, y)
         })
      case x => throw new IllegalArgumentException(x.toString)
    }
  }
}
