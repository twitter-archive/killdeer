package com.twitter.killdeer

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class ResponseSampleServlet(responseSampleFilename: String) extends HttpServlet {
  def txnid(req: HttpServletRequest) = req.getHeader("X-Transaction-Id") match {
    case null => "-"
    case s => s
  }
  val sample = new ResponseSampleLoader(responseSampleFilename)

  override def doGet(req: HttpServletRequest, res: HttpServletResponse) {
    val response = sample.next()
    Thread.sleep(response.latencyMs)
    res.setContentLength(response.size)
    res.setStatus(response.status)
    res.getWriter().write("." * response.size)

    println(response.latencyMs.toString() + " " + txnid(req))
  }
}