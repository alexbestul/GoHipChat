name := "Go-HipChat"

//  Version numbers are in <year>.<release> format.
//  The first release of 2016 is version "16.1".
version := "16.1"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "cd.go.plugin" % "go-plugin-api" % "15.2.0",
  "org.scalaj" %% "scalaj-http" % "2.2.0",
  "org.json4s" %% "json4s-native" % "3.3.0" exclude("org.scala-lang", "scalap"),
  "com.typesafe" % "config" % "1.2.1"
)
