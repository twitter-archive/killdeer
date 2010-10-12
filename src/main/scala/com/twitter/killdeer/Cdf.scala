package com.twitter.killdeer

import scala.util.Random

object Cdf {
  def apply(str: String) = new Cdf(
    for {
      pctile <- str.split(",")
      Array(pct, x) = pctile.split(":")
    } yield (pct.toDouble, x.toInt)
  )

  def apply[T](spec: Tuple2[Double, T]*) = new Cdf(spec)
}

class Cdf[T](spec: Seq[Tuple2[Double, T]]) {
  val rng = new Random

  def apply() = {
    val pctPick = rng.nextFloat
    val Some((_, value)) = spec.find { case (pct, _) => pctPick < pct }
    value
  }
}
