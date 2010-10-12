package com.twitter.killdeer

import java.io._
import java.net.URLEncoder
import org.jboss.netty.handler.codec.http.HttpResponse
import org.jboss.netty.channel._
import org.jboss.netty.buffer.ChannelBuffer

object RecordedResponse {
  val FORMAT = "[^-]+".r
  val maxFileLength = 32

  def load(destination: String, transactionId: String, uri: String) = {
    val file = fileFor(destination, transactionId, uri)
    if (file.canRead) {
      val randomAccessFile = new RandomAccessFile(file, "r")
      Some(new DefaultFileRegion(randomAccessFile.getChannel, 0, randomAccessFile.length))
    } else {
      None
    }
  }

  def open(destination: String, transactionId: String, uri: String) =
    new RecordedResponse(RecordedResponse.fileFor(destination, transactionId, uri))


  private def fileFor(destination: String, transactionId: String, uri: String) = {
    val Seq(timestamp, _, _) = FORMAT.findAllIn(transactionId).toList
    val pathName = destination + timestamp + "/" + transactionId

    val filename = URLEncoder.encode(uri)
    val splitFilename = filename.grouped(maxFileLength).toList
    
    val path = new File(pathName + "/" + splitFilename.take(splitFilename.length - 1).mkString("/"))
    path.mkdirs()
    new File(path, splitFilename.last)
  }
}

class RecordedResponse(file: File) {
  file.delete()
  val fileChannel = new FileOutputStream(file, true).getChannel

  def writeMessage(buffer: ChannelBuffer) {
    fileChannel.write(buffer.toByteBuffer)
  }

  def close() { fileChannel.close() }
}