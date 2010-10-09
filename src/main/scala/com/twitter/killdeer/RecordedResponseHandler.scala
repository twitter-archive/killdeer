package com.twitter.killdeer

import scala.collection.immutable.IndexedSeq
import scala.io.Source
import java.io.File
import com.twitter.util.Timer
import com.twitter.util.TimeConversions._
import scala.collection.mutable.ArrayBuffer
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpHeaders._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.jboss.netty.util.CharsetUtil

class RecordedResponseHandler(timer: Timer, responseSampleDirectory: String) extends SimpleChannelUpstreamHandler {
  def txnid(request: HttpRequest) = request.getHeader("X-Transaction") match {
    case null => "-"
    case s => s
  }
  
  val sampleLoader = new SampleLoader(responseSampleDirectory)

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage.asInstanceOf[HttpRequest]
    val transactionId = txnid(request)
    val recordedResponse = sampleLoader(transactionId)
    val response = new DefaultHttpResponse(HTTP_1_1, OK)
    val channel = e.getChannel
    recordedResponse.headers.foreach { case (headerName, headerValue) =>
      response.addHeader(headerName, headerValue)
    }
    val body = recordedResponse.body + "\n"
    response.addHeader("Content-Length", body.size)
    response.setContent(ChannelBuffers.copiedBuffer(body, CharsetUtil.UTF_8))
    timer.schedule(recordedResponse.latency.millis.fromNow) {
      val writeFuture = channel.write(response)
      if (!HttpHeaders.isKeepAlive(request)) {
        writeFuture.addListener(ChannelFutureListener.CLOSE)
      }
    }
  }
}
