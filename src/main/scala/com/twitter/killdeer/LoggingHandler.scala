package com.twitter.killdeer

import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._

class LoggingHandler extends SimpleChannelUpstreamHandler {
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage.asInstanceOf[HttpRequest]
    println(request.getUri)
    ctx.sendUpstream(e)
  }
}