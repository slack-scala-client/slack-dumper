package slack.dumper

import akka.actor._
import slack.api.SlackApiClient

import scala.concurrent.duration._
import scala.io.Source

object Main extends App {
  val token: String = Source.fromFile("/tmp/slack.api.token").mkString
  implicit val system = ActorSystem("slack")
  implicit val ec = system.dispatcher

  val client = new SlackDumperClient(token, SlackApiClient.defaultSlackApiBaseUri, 5.seconds)
  val selfId = client.state.self.id

  client.onEvent { event =>
    system.log.info("Received new event: {}", event)
  }
}
