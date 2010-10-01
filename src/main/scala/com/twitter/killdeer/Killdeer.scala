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
  def main(args: Array[String]) {
    val responseSampleDirectory = args(0)
    val acceptors = 30

    // Works around a sleep bug in Jetty
    System.setProperty("org.mortbay.io.nio.JVMBUG_THRESHHOLD", Int.MaxValue.toString)

    val server = new KilldeerServer(6666, responseSampleDirectory, acceptors)
    server.start()
  }
}

class KilldeerServer(val port: Int, val responseSampleDirectory: String, val numberOfAcceptors: Int) {
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

  val responseLogDistribution = new ServletHolder(new ResponseSampleServlet(responseSampleDirectory))

  val servletHandler = new ServletHandler
  servletHandler.addServletWithMapping(responseLogDistribution, "/*")

  server.setHandler(servletHandler)

  def start() {
    server.start()
    server.join()
  }
}
