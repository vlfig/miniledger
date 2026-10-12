import scala.sys.process.*

ThisBuild / scalaVersion := "3.10.0"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / organization := "miniledger"

val http4sVersion     = "0.23.38"
val circeVersion      = "0.14.17"
val pureconfigVersion = "0.17.10"
val weaverVersion     = "0.13.0"

// Packages the fat jar, then runs it in a separate JVM on localhost.
val up = taskKey[Unit]("Package the app as a fat jar and run it")

lazy val root = (project in file("."))
  .settings(
    name := "miniledger",
    libraryDependencies ++= Seq(
      "org.http4s"            %% "http4s-ember-server" % http4sVersion,
      "org.http4s"            %% "http4s-dsl"          % http4sVersion,
      "org.http4s"            %% "http4s-circe"        % http4sVersion,
      "io.circe"              %% "circe-generic"       % circeVersion,
      "com.github.pureconfig" %% "pureconfig-core"     % pureconfigVersion,
      "ch.qos.logback"         % "logback-classic"     % "1.5.18" % Runtime,
      "org.typelevel"         %% "weaver-cats"         % weaverVersion % Test,
      "org.typelevel"         %% "weaver-scalacheck"   % weaverVersion % Test,
      "org.http4s"            %% "http4s-client"       % http4sVersion % Test
    ),
    testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
    scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all"),
    Compile / mainClass := Some("miniledger.Main"),
    assembly / mainClass := Some("miniledger.Main"),
    assembly / assemblyJarName := "miniledger.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", _*) => MergeStrategy.concat
      case PathList("META-INF", _*)             => MergeStrategy.discard
      case "module-info.class"                  => MergeStrategy.discard
      case _                                    => MergeStrategy.first
    },
    up := {
      val jar = assembly.value
      val code = Process(Seq("java", "-jar", jar.getAbsolutePath)).!
      if (code != 0 && code != 130 && code != 143) sys.error(s"app exited with $code")
    }
  )

addCommandAlias("serve", "up")
