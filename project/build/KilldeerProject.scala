import sbt._
import com.twitter.sbt._

class KilldeerProject(info: ProjectInfo) extends StandardProject(info) {
  val codehaus = "codehaus" at "http://repository.codehaus.org/"

  val specs = "org.scala-tools.testing" % "specs_2.8.0" % "1.6.5"
  val jetty = "org.eclipse.jetty" % "jetty-http" % "7.1.5.v20100705"
  val jettyServer = "org.eclipse.jetty" % "jetty-server" % "7.1.5.v20100705"
  val jettyServlet = "org.eclipse.jetty" % "jetty-servlet" % "7.1.5.v20100705"
  val jettyCont = "org.eclipse.jetty" % "jetty-continuation" % "7.1.5.v20100705"

  override def mainClass = Some("com.twitter.killdeer.Killdeer")
}