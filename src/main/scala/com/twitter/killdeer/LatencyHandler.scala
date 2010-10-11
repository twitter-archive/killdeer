package com.twitter.killdeer

import org.jboss.netty.channel._
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.util.{Timer, Timeout, TimerTask}
import java.util.concurrent.TimeUnit

class LatencyHandler(timer: Timer, latencyCdf: Cdf[Int]) extends SimpleChannelDownstreamHandler {
  override def writeRequested(ctx: ChannelHandlerContext, e: MessageEvent) {
    val latency = latencyCdf()
    timer.newTimeout(new TimerTask {
      def run(to: Timeout) {
        ctx.sendDownstream(e)
      }
    }, latency, TimeUnit.MILLISECONDS)
  }
}