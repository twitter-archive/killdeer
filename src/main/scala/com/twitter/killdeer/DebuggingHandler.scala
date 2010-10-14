package com.twitter.killdeer

import org.jboss.netty.channel._

class DebuggingHandler extends SimpleChannelHandler {
  override def handleUpstream(ctx: ChannelHandlerContext, e: ChannelEvent) {
    println("UPSTREAM: %s".format(e))
    super.handleUpstream(ctx, e)
  }

  override def handleDownstream(ctx: ChannelHandlerContext, e: ChannelEvent) {
    println("DOWNSTREAM: %s".format(e))
    super.handleDownstream(ctx, e)
  }
}