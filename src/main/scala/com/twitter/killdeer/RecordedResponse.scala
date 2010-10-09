package com.twitter.kildeer

import java.io.{FileWriter, File, BufferedWriter}

object RecordedResponse {
  val FORMAT = "[^-]+".r

  def apply(destination: String, transactionId: String, uri: String) = {
    val Seq(timestamp, _, _) = FORMAT.findAllIn(transactionId).toList
    val filePath = directoryName + timestamp + '/' + transactionId
    RecordedResponse(Source.fromFile(filePath).mkString)
  }

  def apply(response: String) = {
    new RecordedResponse(
      rootNode.path("body").getTextValue,
      headers)
  }

  def apply(response: HttpResponse) {
    
  }

  def fileFor(destination: String, transactionId: String, uri: String) = {
    val pathName = destination + timestamp + "/" + transactionId
    val path = new File(pathName)
    path.mkdirs()
    val fileName = uri
    new File(path, fileName)
  }
}

case class RecordedResponse(transactionId: String, uri: String, body: String) {
  val FORMAT = "[^-]+".r

  def save(destination: String) {
    val Seq(timestamp, _, _) = FORMAT.findAllIn(transactionId).toList
    val file = RecordedResponse.fileFor(destination, transactionId, uri)
    val fileWriter = new FileWriter(file)
    val bufferedWriter = new BufferedWriter(fileWriter)

    bufferedWriter.write(body)
    bufferedWriter.flush()
  }
}