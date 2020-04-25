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
  val salt = scala.util.Random.nextString(10)

  override def onTextMessageReceive(message: TextMessage): Unit = {
    val payload = message.getStrictText
    val payloadJson = Json.parse(payload)

    val typ = (payloadJson \ "type").asOpt[String]

    val anonymized = anonymize(payloadJson)
    println(s">>>>>>> $typ, $anonymized")
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
        JsObject(o.fields.map { f =>
          f._2 match {
            case JsString(SensitiveIdentifiers(s)) =>
              val transformed = s.charAt(0) + transform(s.substring(1))
              (f._1, JsString(transformed))
            case _ =>
              f
          }
        })
      case x => throw new IllegalArgumentException(x.toString)
    }
  }
}
