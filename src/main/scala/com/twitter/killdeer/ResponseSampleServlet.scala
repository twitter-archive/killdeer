package com.twitter.killdeer

import org.eclipse.jetty.continuation.ContinuationSupport
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import net.lag.logging.Logger
import scala.io.Source

class ResponseSampleServlet(responseSampleDirectory: String) extends HttpServlet {
  def txnid(req: HttpServletRequest) = req.getHeader("X-Transaction") match {
    case null => "-"
    case s => s
  }
  
  val sampleLoader = new SampleLoader(responseSampleDirectory)

  override def doGet(req: HttpServletRequest, res: HttpServletResponse) {
    val transactionId = txnid(req)
    val recordedResponse = sampleLoader(transactionId)
    Thread.sleep(recordedResponse.latency)
    res.setStatus(200)
    recordedResponse.headers.foreach { case (headerName, headerValue) =>
      res.addHeader(headerName, headerValue)
    }
    res.getWriter.write(recordedResponse.body)
  }
}
