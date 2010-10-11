package com.twitter.killdeer

import org.jboss.netty.channel._
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._

class DotsHandler(responseSizeCdf: Cdf[Int]) extends SimpleChannelUpstreamHandler {
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val channel = e.getChannel
    val length = responseSizeCdf()
    val response = {
      val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
      response.setHeader("Content-Length", length)
      response.setContent(ChannelBuffers.wrappedBuffer {
        val content = new Array[Byte](length)
        java.util.Arrays.fill(content, '.'.toByte)
        content
      })
      response
    }
    channel.write(response).addListener(new ChannelFutureListener {
      def operationComplete(future: ChannelFuture) {
        channel.close()
      }
    })
  }
}