// Copyright 2010 Twitter, Inc.
//
// Fault injection for netty channels.

package com.twitter.killdeer

import java.util.concurrent.TimeUnit

import util.Random

import org.jboss.netty.channel.{
  Channels, Channel, ChannelEvent, ChannelFuture,
  SimpleChannelUpstreamHandler, SimpleChannelDownstreamHandler,
  ChannelUpstreamHandler, ChannelHandlerContext, ChannelFutureListener,
  LifeCycleAwareChannelHandler, MessageEvent, ChannelState,
  DownstreamChannelStateEvent, ChannelStateEvent}
import org.jboss.netty.util.{HashedWheelTimer, Timeout, TimerTask}

object FaultInjector {
  val timer = new HashedWheelTimer(1, TimeUnit.MILLISECONDS)
}

trait FaultInjector {
  def fault(channel: Channel)

  var fired = false
  val fireOnce = false
  def fire(channel: Channel) {
    if (!fired || !fireOnce)
      fault(channel)
    fired = true
  }
}

object NoFaultInjector extends FaultInjector {
  def fault(channel: Channel) = ()
}

class TimeoutFaultInjector extends FaultInjector {
  override val fireOnce = true

  def fault(channel: Channel) {
    channel.getPipeline.addFirst(
      "timeout", new SimpleChannelDownstreamHandler {
        override def writeRequested(ctx: ChannelHandlerContext, m: MessageEvent) {
          // (do nothing.. don't pass it on)
        }
      })
  }
}

class ConnectionDisconnectFaultInjector extends FaultInjector {
  def fault(channel: Channel) {
    // We sink the event directly so that nothing in the pipeline sees
    // our mischevious actions.
    val future = Channels.future(channel)
    val event = new DownstreamChannelStateEvent(channel, future, ChannelState.CONNECTED, null)
    channel.getPipeline.getSink.eventSunk(channel.getPipeline, event)
  }
}

// Frequencies given are per request-second. This will approximate a
// set of Poisson processes. One difference: we're picking from a
// common CDF to achieve mutual exclusion. Thus, we have correlation &
// not a strict Poisson process.
class UpstreamFaultInjectorHandler(injectors: Tuple2[FaultInjector, Double]*)
extends SimpleChannelUpstreamHandler with LifeCycleAwareChannelHandler
{
  val rng = new Random
  @volatile var timeout: Timeout = _

  // Construct a CDF of the faults so we can easily pick from our
  // distribution.
  val faultCDF = {
    val injectorCDF =
      injectors.foldLeft(Nil: List[Tuple2[Double, FaultInjector]]) {
        case (Nil, (injector, freq))                   => (freq / 1000.0, injector) :: Nil
        case (cdf@((freq, _) :: _), (injector, freq_)) => (freq + freq_ / 1000.0, injector) :: cdf
      }

    ((1.0, NoFaultInjector) :: injectorCDF).reverse
  }

  def start(channel: Channel) {
    timeout = FaultInjector.timer.newTimeout(
      new TimerTask {
        def run(to: Timeout) {
          if (!channel.isOpen || timeout.isCancelled)
            return

          PickFromCDF(faultCDF).fire(channel)
          timeout = FaultInjector.timer.newTimeout(this, 1, TimeUnit.MILLISECONDS)
        }
      }, 1, TimeUnit.MILLISECONDS)
  }

  def stop() {
    if (timeout ne null) {
      timeout.cancel()
      timeout = null
    }
  }

  def afterAdd(ctx: ChannelHandlerContext) {}
  def afterRemove(ctx: ChannelHandlerContext) {}

  def beforeAdd(ctx: ChannelHandlerContext) {
    if (ctx.getPipeline.isAttached)
      start(ctx.getChannel)
  }

  def beforeRemove(ctx: ChannelHandlerContext)  {
    stop()
  }

  override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    start(ctx.getChannel)
    ctx.sendUpstream(e)
  }

  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    stop()
    ctx.sendUpstream(e)
  }
}
