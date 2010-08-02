package com.twitter.killdeer

import scala.collection.immutable.IndexedSeq
import scala.io.Source
import java.io.File

case class Response(latencyMs: Int, size: Int, status: Int) {
  def this(parts: Array[String]) = {
    this((parts(0).toFloat * 1000).toInt, parts(1).toInt, parts(2).toInt)
  }

  def this(line: String) = {
    this(line.split(" "))
  }
}

/**
 * @param latencyFile a relative or absolute path to a file filled with latency numbers
 * formatted as space-separated-values:
 * latency_in_seconds document_size status_code
 */
class ResponseSampleLoader(sampleFile: File) {
  val responseSample: IndexedSeq[Response] = IndexedSeq.empty ++ Source.fromFile(sampleFile).getLines.map(line => new Response(line))
  var currentPosition = 0
  lazy val size: Int = responseSample.size

  def this(filename: String) = {
    this(new File(filename))
  }

  def next(): Response = {
    if (currentPosition >= size) {
      currentPosition = 0
    }
    val response = responseSample(currentPosition)
    currentPosition += 1
    response
  }
}
