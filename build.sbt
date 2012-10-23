name := "TwitOnHip"

version := "0.1"

resolvers += "repo.codahale.com" at "http://repo.codahale.com/"

resolvers += "typesafe.com" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "sonatype" at "http://oss.sonatype.org/content/repositories/releases"

libraryDependencies += "org.scalaz" %% "scalaz-core" % "6.0.4"

libraryDependencies += "com.github.ornicar" % "scalalib_2.9.1" % "2.6"

libraryDependencies += "org.specs2" %% "specs2" % "1.12"

libraryDependencies += "net.databinder.dispatch" %% "dispatch-core" % "0.9.2"

libraryDependencies += "joda-time" % "joda-time" % "2.0"

libraryDependencies += "net.liftweb" % "lift-json_2.9.1" % "2.4"

libraryDependencies += "org.scala-tools.time" % "time_2.9.1" % "0.5"
