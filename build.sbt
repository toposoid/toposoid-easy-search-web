name := """toposoid-easy-search-web"""
organization := "com.ideal.linked"

version := "0.5"

lazy val root = (project in file(".")).enablePlugins(PlayScala).enablePlugins(AutomateHeaderPlugin)

organizationName := "Linked Ideal LLC.[https://linked-ideal.com/]"
startYear := Some(2021)
licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))


scalaVersion := "2.13.11"

libraryDependencies += guice
libraryDependencies += "com.ideal.linked" %% "scala-common" % "0.5"
libraryDependencies += "com.ideal.linked" %% "toposoid-knowledgebase-model" % "0.5"
libraryDependencies += "com.ideal.linked" %% "toposoid-feature-vectorizer" % "0.5"
libraryDependencies += "com.ideal.linked" %% "toposoid-deduction-common" % "0.5"
libraryDependencies += "com.ideal.linked" %% "toposoid-sentence-transformer-neo4j" % "0.5" % Test
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test

