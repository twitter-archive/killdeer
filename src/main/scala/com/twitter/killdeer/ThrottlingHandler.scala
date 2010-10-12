package com.twitter.killdeer

import scala.math

import java.util.concurrent.TimeUnit

import org.jboss.netty.channel._
import org.jboss.netty.buffer.{ChannelBuffers, ChannelBuffer, CompositeChannelBuffer}
import org.jboss.netty.handler.queue.BufferedWriteHandler
import org.jboss.netty.util.{Timer, TimerTask, Timeout}

abstract class BufferingThrottler(rateBps: Int, timer: Timer) {
  val buffer = ChannelBuffers.dynamicBuffer()

  // Minimum granularity: 10ms.
  val rateBpms = rateBps.toFloat / 1000.0
  val tickMs = math.max(10, 1 / rateBpms).toInt
  val bytesPerTick = math.max(1, math.ceil(rateBpms * tickMs).toInt)

  @volatile var timeout: Timeout = _
  val task = new TimerTask {
    def run(to: Timeout) {
      if (to.isCancelled)
        return

      val channelBuffer =
        buffer.synchronized {
          if (buffer.readableBytes > 0)
            Some(buffer.readBytes(math.min(bytesPerTick, buffer.readableBytes)))
          else
            None
        }

      channelBuffer.foreach(write)
      timeout = timer.newTimeout(this, tickMs, TimeUnit.MILLISECONDS)
    }
  }

  timeout = timer.newTimeout(task, tickMs, TimeUnit.MILLISECONDS)

  def sink(in: ChannelBuffer) {
    buffer.synchronized {
      buffer.writeBytes(in)
    }
  }

  def stop() = {
    timeout.cancel()
    if (buffer.readableBytes > 0)
      write(buffer)
  }

  // Implement this!
  def write(out: ChannelBuffer)
}

// These need to be inserted very low in the stack-- it needs access
// to ChannelBuffers directly, and not decoded POJOs.
class DownstreamThrottlingHandler(rateBps: Int, timer: Timer)
extends SimpleChannelDownstreamHandler
{
  var ctx: ChannelHandlerContext = _

  val throttler = new BufferingThrottler(rateBps, timer) {
    def write(out: ChannelBuffer)  {
      if (ctx ne null) {
        // We don't care about the results.
        val future = Channels.future(ctx.getChannel)
        Channels.write(ctx, future, out)
      }
    }
  }

  override def writeRequested(ctx: ChannelHandlerContext, e: MessageEvent) {
    this.ctx = ctx
    e.getMessage match {
      case buffer: ChannelBuffer => throttler.sink(buffer)
      case _ => super.writeRequested(ctx, e)
    }
  }

  override def closeRequested(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    throttler.stop()
    ctx.sendUpstream(e)
  }
}
