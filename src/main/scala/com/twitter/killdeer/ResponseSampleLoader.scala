package com.twitter.killdeer

import scala.collection.immutable.IndexedSeq
import scala.io.Source
import java.io.File
import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.map.ObjectMapper
import scala.collection.mutable.ArrayBuffer

class SampleLoader(directoryName: String) {
  val FORMAT = "[^-]+".r
  def apply(transactionId: String) = {
    val Seq(timestamp, _, _) = FORMAT.findAllIn(transactionId).toList
    val filePath = directoryName + timestamp + '/' + transactionId
    RecordedResponse(Source.fromFile(filePath).mkString)
  }
}

object RecordedResponse {
  def apply(json: String) = {
    val mapper = new ObjectMapper
    val rootNode = mapper.readValue(json, classOf[JsonNode])
    val headerNodes = rootNode.path("headers").getElements
    val headers = new ArrayBuffer[(String, String)]
    while (headerNodes.hasNext) {
      val headerNode = headerNodes.next()
      val header = (headerNode.get(0).getTextValue, headerNode.get(1).getTextValue)
      headers += header
    }
    
    new RecordedResponse(
      rootNode.path("body").getTextValue,
      headers,
      rootNode.path("latency").getValueAsLong)
  }
}

case class RecordedResponse(body: String, headers: Collection[(String, String)], latency: Long)
