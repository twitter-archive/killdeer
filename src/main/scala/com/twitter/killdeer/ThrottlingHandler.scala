package com.twitter.killdeer

import scala.math
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConversions._

import java.util.concurrent.TimeUnit

import org.jboss.netty.channel._
import org.jboss.netty.buffer.{ChannelBuffers, ChannelBuffer, CompositeChannelBuffer}
import org.jboss.netty.handler.queue.BufferedWriteHandler
import org.jboss.netty.util.{Timer, TimerTask, Timeout}

abstract class BufferingThrottler(rateBps: Int, timer: Timer) {
  var events = List[MessageEvent]()
  var ctx: ChannelHandlerContext = _

  // Minimum granularity: 10ms.
  val rateBpms = rateBps.toFloat / 1000.0
  val tickMs = math.max(10, 1 / rateBpms).toInt
  val bytesPerTick = math.max(1, math.ceil(rateBpms * tickMs).toInt)

  @volatile var timeout: Timeout = _
  val task = new TimerTask {
    def run(to: Timeout) {
      if (to.isCancelled)
        return

      doWrite(bytesPerTick)
      timeout = timer.newTimeout(this, tickMs, TimeUnit.MILLISECONDS)
    }
  }

  timeout = timer.newTimeout(task, tickMs, TimeUnit.MILLISECONDS)

  def sink(ctx: ChannelHandlerContext, e: MessageEvent) {
    assert(e.getMessage.isInstanceOf[ChannelBuffer])
    this.ctx = ctx

    synchronized {
      events = events :+ e
    }
  }

  def stop() = {
    timeout.cancel()
    doWrite(Int.MaxValue)
  }

  def doWrite(howmuch: Int) = {
    val writeBuffers = new ListBuffer[ChannelBuffer]()
    val writeFutures = new ListBuffer[ChannelFuture]()

    synchronized {
      var written = 0

      while (written < howmuch && !events.isEmpty) {
        var event = events.head
        val buffer = event.getMessage.asInstanceOf[ChannelBuffer]
    
        val toWrite = math.min(howmuch - written, buffer.readableBytes)
        written += toWrite

        writeBuffers.append(buffer.readBytes(toWrite))
        if (!buffer.readable) {
          events = events.tail
          writeFutures.append(event.getFuture)
        }
      }
    }

    if (!writeBuffers.isEmpty) {
      val composite = ChannelBuffers.wrappedBuffer(writeBuffers.toArray:_*)

      val future = Channels.future(ctx.getChannel)
      future.addListener(new ChannelFutureListener() {
        def operationComplete(future: ChannelFuture) {
          if (future.isSuccess)
            writeFutures.foreach(_.setSuccess())
          else
            writeFutures.foreach(_.setFailure(future.getCause))
        }
      })
      write(composite, future)
    }
  }


  // Implement this!
  def write(out: ChannelBuffer, future: ChannelFuture)
}

// These need to be inserted very low in the stack-- it needs access
// to ChannelBuffers directly, and not decoded POJOs.
class DownstreamThrottlingHandler(rateBps: Int, timer: Timer)
extends SimpleChannelDownstreamHandler
{
  var ctx: ChannelHandlerContext = _

  val throttler = new BufferingThrottler(rateBps, timer) {
    def write(out: ChannelBuffer, future: ChannelFuture)  {
      if (ctx ne null)
        Channels.write(ctx, future, out)
    }
  }

  override def writeRequested(ctx: ChannelHandlerContext, e: MessageEvent) = {
    this.ctx = ctx
    throttler.sink(ctx, e)
  }

  override def closeRequested(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    // The upstream handler waits for the write futures to complete
    // before closing, so we really shouldn't have any issues with
    // buffering here.
    throttler.stop()
    ctx.sendUpstream(e)
  }
}
