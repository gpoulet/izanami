import sbt.Keys.{organization, scalacOptions}
import sbtrelease.ReleaseStateTransformations._
import BintrayConfig._

val disabledPlugins = if (sys.env.get("TAG_NAME").filterNot(_.isEmpty).isDefined) {
  Seq(RevolverPlugin)
} else {
  Seq(RevolverPlugin, BintrayPlugin)
}

scalaVersion := "2.13.3"

val akkaVersion     = "2.6.3"
val akkaHttpVersion = "10.1.11"

lazy val jvm = (project in file("."))
  .disablePlugins(disabledPlugins: _*)
  .settings(
    organization := "fr.maif",
    name := "izanami-client",
//    crossScalaVersions := Seq(scalaVersion.value, "2.12.11"),
    libraryDependencies ++= Seq(
        "com.typesafe.akka"          %% "akka-stream"             % akkaVersion,
        "com.typesafe.akka"          %% "akka-slf4j"              % akkaVersion,
        "com.typesafe.akka"          %% "akka-http"               % akkaHttpVersion,
        "com.lightbend.akka"         %% "akka-stream-alpakka-sse" % "2.0.2",
        "org.scala-lang.modules"     %% "scala-collection-compat" % "2.1.2",
        "io.vavr"                    % "vavr"                     % "0.10.0",
        "org.reactivecouchbase.json" % "json-lib"                 % "1.0.0",
        "com.google.guava"           % "guava"                    % "25.1-jre",
        "com.typesafe.play"          %% "play-json"               % "2.7.4",
        "com.chuusai"                %% "shapeless"               % "2.3.3",
        "junit"                      % "junit"                    % "4.12" % Test,
        "org.assertj"                % "assertj-core"             % "3.5.2" % Test,
        "com.novocode"               % "junit-interface"          % "0.11" % Test,
        "org.scalatest"              %% "scalatest"               % "3.0.8" % Test,
        "com.typesafe.akka"          %% "akka-testkit"            % akkaVersion % Test,
        "org.mockito"                % "mockito-core"             % "2.12.0" % Test,
        "com.github.tomakehurst"     % "wiremock-jre8"            % "2.24.1" % Test,
        "org.assertj"                % "assertj-core"             % "3.8.0" % Test
      ),
    resolvers ++= Seq(
        Resolver.jcenterRepo,
        Resolver.bintrayRepo("larousso", "maven")
      )
  )
  .settings(publishSettings: _*)

scalacOptions ++= Seq(
//  "-Ypartial-unification",
  "-feature",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:existentials",
//  "-Xfatal-warnings",
  "-Ywarn-unused:imports",
  "-Yrangepos",
  "-deprecation"
)
//addCompilerPlugin(scalafixSemanticdb)
scalafixDependencies in ThisBuild += "org.scala-lang.modules" %% "scala-collection-migrations" % "2.1.2"
