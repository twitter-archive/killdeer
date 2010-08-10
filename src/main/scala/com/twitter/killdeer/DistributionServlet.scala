package com.twitter.killdeer

import cern.jet.random.engine.DRand
import cern.jet.random.AbstractDistribution
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

abstract class DistributionServlet extends HttpServlet {
  val latencyGenerator: AbstractDistribution

  val prng = new DRand()

  override def doGet(req: HttpServletRequest, res: HttpServletResponse) {
    // We use the latency for both how long to sleep and how large a document to respond with.
    val latency = latencyGenerator.nextInt().abs
    Thread.sleep(latency)
    res.setStatus(200)
    res.setContentLength(latency)
    res.getWriter().write("." * latency)
    res.getWriter().write("\n")
  }
}


import cern.jet.random.Normal

class NormalDistributionServlet(mean: Double, stddev: Double) extends DistributionServlet {
  val latencyGenerator = new Normal(mean, stddev, prng)
}

import cern.jet.random.Exponential

class ExponentialDistributionServlet(lambda: Double) extends DistributionServlet {
  val latencyGenerator = new Exponential(lambda, prng)
}

import cern.jet.random.Logarithmic

class LogarithmicDistributionServlet(p: Double) extends DistributionServlet {
  val latencyGenerator = new Logarithmic(p, prng)
}

import cern.jet.random.Zeta

class ZipfDistributionServlet(ro: Double, pk: Double) extends DistributionServlet {
  val latencyGenerator = new Zeta(ro, pk, prng)
}
