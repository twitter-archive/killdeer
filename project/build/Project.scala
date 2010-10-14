import sbt._
import com.twitter.sbt._

class Project(info: ProjectInfo) extends StandardProject(info) {
  val codehaus = "codehaus" at "http://repository.codehaus.org/"
  val jboss = "repository.jboss.org" at "http://repository.jboss.org/nexus/content/groups/public/"

  val specs = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5"
  val util = "com.twitter" %% "util" % "1.2-SNAPSHOT"

  // Necessary for Eval because of a bug in SBT:
  val scalaTools = "org.scala-lang" % "scala-compiler" % "2.8.0" % "compile"
  override def filterScalaJars = false

  val netty = "org.jboss.netty" % "netty" % "3.2.2.Final"
  val jacksonCore = "org.codehaus.jackson" % "jackson-core-asl" % "1.6.0"
  val jacksonMapper = "org.codehaus.jackson" % "jackson-mapper-asl" % "1.6.0"

  override def mainClass = Some("com.twitter.killdeer.Main")
}
