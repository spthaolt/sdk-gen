import sbt._
import Keys._

object Build extends Build {

  lazy val raml = RootProject(file("../raml-scala-parser"))

  lazy val defaultSettings =
    Defaults.defaultSettings ++
      Seq(
        name := "sdk-gen",
        version := "1.0",
        scalaVersion := "2.10.2",
        scalacOptions := Seq(
          "-feature",
          "-language:implicitConversions",
          "-language:postfixOps",
          "-unchecked",
          "-deprecation",
          "-encoding", "utf8",
          "-Ywarn-adapted-args"))


  lazy val root = Project("root",
    file("."),
    settings = defaultSettings ++ Seq(
      resolvers ++= Seq(
        "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
        "Maven repository" at "http://morphia.googlecode.com/svn/mavenrepo/"),
      libraryDependencies ++= Seq(
        "junit" % "junit" % "4.11",
        "org.scalatest" % "scalatest_2.10" % "2.0",
        "com.typesafe" % "config" % "1.0.2",
        "com.github.scopt" %% "scopt" % "3.2.0",
        "org.apache.httpcomponents" % "httpclient" % "4.3.1",
        "org.json" % "json" % "20131018",
        "org.fusesource.scalate" % "scalate-core_2.10" % "1.6.1",
        "org.mockito" % "mockito-all" % "1.9.5"
        )))
    .dependsOn(raml)

}