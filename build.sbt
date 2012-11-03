name := "HipMonad"

version := "0.1"

scalaVersion := "2.9.2"

seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)

resolvers ++= Seq(
  "repo.codahale.com" at "http://repo.codahale.com/",
  "typesafe.com" at "http://repo.typesafe.com/typesafe/releases/",
  "sonatype" at "http://oss.sonatype.org/content/repositories/releases",
  "jirafe-github" at "https://raw.github.com/jirafe/mvn-repo/master/releases")

libraryDependencies ++= Seq(
  "com.github.ornicar" % "scalalib_2.9.1" % "2.6",
  "net.databinder.dispatch" %% "dispatch-core" % "0.9.2",
  "joda-time" % "joda-time" % "2.0",
  "net.liftweb" % "lift-json_2.9.1" % "2.4",
  "org.scala-tools.time" % "time_2.9.1" % "0.5")
