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
    val (samplesFilename: String, acceptors: Int) = args.length match {
      case 0 => ("config/response-sample.txt", 10000)
      case 1 => (args(0), 10000)
      case 2 => (args(0), args(1).toInt)
      case _ => {
        println("usage: com.twitter.killdeer.Killdeer <path/to/latencies.txt> [acceptors]")
        System.exit(-1)
      }
    }

    val server = new KilldeerServer(6666, samplesFilename, acceptors)
    server.start()
  }
}

class KilldeerServer(val port: Int, val responseSampleFilename: String, val numberOfAcceptors: Int) {
  println("responseSampleFilename: %s".format(responseSampleFilename))
  println("number of acceptors: %d".format(numberOfAcceptors))

  val server = new Server()
  val connector = new SelectChannelConnector
  connector.setPort(port)
  connector.setMaxIdleTime(30000)
  connector.setRequestHeaderSize(8192)
  connector.setThreadPool(new ExecutorThreadPool(numberOfAcceptors))
  server.addConnector(connector)

  val responseLogDistribution = new ServletHolder(new ResponseSampleServlet(responseSampleFilename))

  val servletHandler = new ServletHandler
  servletHandler.addServletWithMapping(responseLogDistribution, "/*")

  server.setHandler(servletHandler)

  def start() {
    server.start()
    server.join()
  }
}
