package slack.dumper

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.TextMessage
import play.api.libs.json.Json
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
  override def onTextMessageReceive(message: TextMessage): Unit = {
    val payload = message.getStrictText
    val payloadJson = Json.parse(payload)

    val typ = (payloadJson \ "type").asOpt[String]
    println(s">>>>>>> $typ, $payload")
  }
}
