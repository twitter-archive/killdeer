package com.twitter.killdeer

object RecordedRequest {
  val FORMAT = """\S+""".r
  def apply(line: String) = {
    val Seq(transactionId, userId, _, _, _, _, method, uri, _, latency) = FORMAT.findAllIn(line).toList
    new RecordedRequest(transactionId, userId.toInt, method, uri)
  }
}

case class RecordedRequest(transactionId: String, userId: Int, method: String, uri: String)
