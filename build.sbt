import Dependencies._

ThisBuild / scalaVersion     := "2.13.6"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.github.slack-scala-client"
ThisBuild / organizationName := "Slack Scala Client"

lazy val root = (project in file("."))
  .settings(
    name := "Slack Dumper",
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "com.github.slack-scala-client" %% "slack-scala-client" % "0.2.16",
    libraryDependencies += "commons-codec" % "commons-codec" % "1.15"
  )

