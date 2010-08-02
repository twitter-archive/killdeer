package com.twitter.killdeer

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class ResponseSampleServlet(responseSampleFilename: String) extends HttpServlet {
  val sample = new ResponseSampleLoader(responseSampleFilename)

  override def doGet(req: HttpServletRequest, res: HttpServletResponse) {
    val response = sample.next()
    Thread.sleep(response.latencyMs)
    res.setContentLength(response.size)
    res.setStatus(response.status)
    res.getWriter().write("." * response.size)
  }
}