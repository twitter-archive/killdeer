package com.twitter.killdeer

import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{DefaultServlet, FilterHolder, ServletContextHandler, ServletHandler, ServletHolder}
import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.eclipse.jetty.util.thread.ExecutorThreadPool
import org.eclipse.jetty.continuation.{Continuation, ContinuationSupport}
import javax.servlet.Servlet


object Killdeer {
  def main(args: Array[String]) {
    if (args.length < 1) {
      println("usage: com.twitter.killdeer.Killdeer <path/to/latencies.txt>")
      System.exit(-1)
    }
    val server = new KilldeerServer(6666, args(0))
    server.start()
  }
}

class KilldeerServer(val port: Int, val responseSampleFilename: String) {
  val server = new Server()
  val connector = new SelectChannelConnector
  connector.setPort(port)
  connector.setMaxIdleTime(30000)
  connector.setRequestHeaderSize(8192)
  connector.setThreadPool(new ExecutorThreadPool(20))
  server.addConnector(connector)

  val servletHandler = new ServletHandler
  val sampleHolder = new ServletHolder(new ResponseSampleServlet(responseSampleFilename))
  servletHandler.addServletWithMapping(sampleHolder, "/*")
  server.setHandler(servletHandler)

  def start() {
    server.start()
    server.join()
  }
}