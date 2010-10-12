package com.twitter.killdeer

import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.buffer.ChannelBuffer
import java.io.File

class ResponseRecorderHandler(sampleDirectory: String) extends SimpleChannelHandler {
  case class Request(transactionId: String, uri: String)

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage.asInstanceOf[HttpRequest]
    var recorder = ctx.getAttachment.asInstanceOf[RecordedResponse]
    if (recorder != null) recorder.close()
    val transactionId = request.getHeader("X-Transaction")
    
    if (transactionId != null) {
      recorder = RecordedResponse.open(sampleDirectory, transactionId, request.getUri)
      ctx.setAttachment(recorder)
    }
    ctx.sendUpstream(e)
  }

  override def writeRequested(ctx: ChannelHandlerContext, e: MessageEvent) {
    val response = e.getMessage.asInstanceOf[ChannelBuffer]
    val recorder = ctx.getAttachment.asInstanceOf[RecordedResponse]
    if (recorder != null) recorder.writeMessage(response)
    ctx.sendDownstream(e)
  }

  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val recorder = ctx.getAttachment.asInstanceOf[RecordedResponse]
    if (recorder != null) recorder.close()
    ctx.sendDownstream(e)
  }
}