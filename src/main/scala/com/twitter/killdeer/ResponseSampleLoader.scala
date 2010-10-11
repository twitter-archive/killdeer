package com.twitter.killdeer

import scala.collection.immutable.IndexedSeq
import scala.io.Source
import java.io.File
import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.map.ObjectMapper
import scala.collection.mutable.ArrayBuffer

import org.jboss.netty.buffer.{ChannelBuffers, ChannelBuffer}
import org.jboss.netty.util.CharsetUtil


trait ResponseSource {
  def apply(transactionId: String): RecordedResponse
}

class SampleLoader(directoryName: String) extends ResponseSource {
  val FORMAT = "[^-]+".r
  def apply(transactionId: String) = {
    println(transactionId)
    val Seq(timestamp, _, _) = FORMAT.findAllIn(transactionId).toList
    val filePath = directoryName + timestamp + '/' + transactionId
    RecordedResponse(Source.fromFile(filePath).mkString)
  }
}

object RecordedResponse {
  def apply(json: String): RecordedResponse = {
    val mapper = new ObjectMapper
    val rootNode = mapper.readValue(json, classOf[JsonNode])
    val headerNodes = rootNode.path("headers").getElements
    val headers = new ArrayBuffer[(String, String)]
    while (headerNodes.hasNext) {
      val headerNode = headerNodes.next()
      val header = (headerNode.get(0).getTextValue, headerNode.get(1).getTextValue)
      headers += header
    }
    
    apply(
      rootNode.path("body").getTextValue + "\n",
      headers,
      rootNode.path("latency").getValueAsLong)
  }

  def apply(body: String, headers: Collection[(String, String)], latency: Long): RecordedResponse =
    RecordedResponse(ChannelBuffers.copiedBuffer(body, CharsetUtil.UTF_8), headers, latency)

}

case class RecordedResponse(
  body: ChannelBuffer,
  headers: Collection[(String, String)],
  latency: Long)
