package com.twitter.killdeer

import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{DefaultServlet, FilterHolder, ServletContextHandler, ServletHandler, ServletHolder}
import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.eclipse.jetty.util.thread.ExecutorThreadPool
import org.eclipse.jetty.continuation.{Continuation, ContinuationSupport}
import javax.servlet.Servlet
import net.lag.configgy.RuntimeEnvironment

object Killdeer {
  val runtime = new RuntimeEnvironment(getClass)

  def main(args: Array[String]) {
    runtime.load(args)

    val (samplesFilename: String, acceptors: Int) = args.length match {
      case 0 => ("config/response-sample.txt", 30)
      case 1 => (args(0), 30)
      case 2 => (args(0), args(1).toInt)
      case _ => {
        println("usage: com.twitter.killdeer.Killdeer <path/to/latencies.txt> [acceptors]")
        System.exit(-1)
      }
    }

    // Works around a sleep bug in Jetty
    System.setProperty("org.mortbay.io.nio.JVMBUG_THRESHHOLD", Int.MaxValue.toString)

    val server = new KilldeerServer(6666, samplesFilename, acceptors)
    server.start()
  }
}

class KilldeerServer(val port: Int, val responseSampleFilename: String, val numberOfAcceptors: Int) {
  println("responseSampleFilename: %s".format(responseSampleFilename))
  println("number of acceptors: %d".format(numberOfAcceptors))

  val server = new Server(port)
  val conn = new SelectChannelConnector

  var acceptors = 2
  var maxIdleTimeMS = 1000
  var lowResourcesMaxIdleTimeMS = 300
  var lowResourcesConnections = 200
  var resolveNames = false
  var reuseAddress = true
  var headerBufferSize = 4192
  var requestBufferSize = 16 * 1024
  var responseBufferSize = 16 * 1024
  var soLingerSecs = -1

  conn.setAcceptors(acceptors)
  conn.setMaxIdleTime(maxIdleTimeMS)
  conn.setAcceptQueueSize(100)
  conn.setLowResourcesConnections(lowResourcesConnections)
  conn.setLowResourceMaxIdleTime(lowResourcesMaxIdleTimeMS)
  conn.setResolveNames(resolveNames)
  conn.setReuseAddress(reuseAddress)
  conn.setHeaderBufferSize(headerBufferSize)
  conn.setRequestBufferSize(requestBufferSize)
  conn.setResponseBufferSize(responseBufferSize)
  conn.setSoLingerTime(soLingerSecs)

  server.addConnector(conn)

  val responseLogDistribution = new ServletHolder(new ResponseSampleServlet(responseSampleFilename))

  val servletHandler = new ServletHandler
  servletHandler.addServletWithMapping(responseLogDistribution, "/*")

  server.setHandler(servletHandler)

  def start() {
    server.start()
    server.join()
  }
}
