package com.twitter.killdeer

import java.io._
import java.net.URLEncoder
import org.jboss.netty.handler.codec.http.HttpResponse
import org.jboss.netty.channel._
import org.jboss.netty.buffer.ChannelBuffer

object RecordedResponse {
  val FORMAT = "[^-]+".r

  def load(destination: String, transactionId: String, uri: String) = {
    val file = fileFor(destination, transactionId, uri)
    if (file.canRead) {
      val randomAccessFile = new RandomAccessFile(file, "r")
      Some(new DefaultFileRegion(randomAccessFile.getChannel, 0, randomAccessFile.length))
    } else {
      None
    }
  }

  def save(destination: String, transactionId: String, uri: String, buffer: ChannelBuffer) {
    val file = RecordedResponse.fileFor(destination, transactionId, uri)
    val fileChannel = new FileOutputStream(file, false).getChannel
    fileChannel.write(buffer.toByteBuffer)
  }

  private def fileFor(destination: String, transactionId: String, uri: String) = {
    val Seq(timestamp, _, _) = FORMAT.findAllIn(transactionId).toList
    val pathName = destination + timestamp + "/" + transactionId
    val path = new File(pathName)
    path.mkdirs()
    val filename = URLEncoder.encode(uri)
    new File(path, filename)
  }
}