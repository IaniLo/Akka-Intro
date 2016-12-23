name := "hello-akka"

version := "1.0"

scalaVersion := "2.11.8"

lazy val akkaVersion = "2.4.4"

// https://mvnrepository.com/artifact/org.jsoup/jsoup
libraryDependencies += "org.jsoup" % "jsoup" % "1.9.2"


resolvers ++= Seq("sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots", 
                        "sonatype releases" at "https://oss.sonatype.org/content/repositories/releases")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.json4s" % "json4s-native_2.11" % "3.4.2",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "junit" % "junit" % "4.12" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.14",
  "com.ning" % "async-http-client" % "1.7.9",
  "org.slf4j" % "slf4j-api" % "1.7.21",
  "org.slf4j" % "slf4j-simple" % "1.7.21"
)

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")
