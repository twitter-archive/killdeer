package com.twitter.killdeer

import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.buffer.ChannelBuffer
import java.io.File

class ResponseRecorderHandler(sampleDirectory: String) extends SimpleChannelHandler {
  case class Request(transactionId: String, uri: String)

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage.asInstanceOf[HttpRequest]
    ctx.setAttachment(request)
    ctx.sendUpstream(e)
  }

  override def writeRequested(ctx: ChannelHandlerContext, e: MessageEvent) {
    val response = e.getMessage.asInstanceOf[ChannelBuffer]
    val request = ctx.getAttachment.asInstanceOf[HttpRequest]
    RecordedResponse.save(sampleDirectory, request.getHeader("X-Transaction"), request.getUri, response)
    ctx.sendDownstream(e)
  }

  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    ctx.sendDownstream(e)
  }
}