package com.twitter.killdeer

import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpHeaders
import java.io.File

class KeepAliveHandler(sampleDirectory: String) extends SimpleChannelHandler {
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage.asInstanceOf[HttpRequest]
    ctx.setAttachment(HttpHeaders.isKeepAlive(request))
    ctx.sendUpstream(e)
  }

  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    if (!ctx.getAttachment.asInstanceOf[Boolean]) {
       ctx.sendDownstream(e)
    }
  }
}