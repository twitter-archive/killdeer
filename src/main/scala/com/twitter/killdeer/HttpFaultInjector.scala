package com.twitter.killdeer

import scala.util.Random

import org.jboss.netty.handler.codec.http._
import org.jboss.netty.channel._

// These fault injectors are per-{request,response} rather than based
// on request time. They must sit after the http message encoder.

trait HttpFaultInjector {
  def faultMessage(httpMessage: HttpMessage) = ()
  def faultChunk(httpChunk: HttpChunk): Option[HttpChunk] = Some(httpChunk)
}

object NoHttpFaultInjector extends HttpFaultInjector

object ContentLengthManglingHttpFaultInjector extends HttpFaultInjector {
  override def faultMessage(httpMessage: HttpMessage) {
    val newContentLength = HttpHeaders.getContentLength(httpMessage) + 1
    HttpHeaders.setHeader(httpMessage, "Content-Length", newContentLength)
  }
}

class ChunkDroppingHttpFaultInjector(rate: Float) extends HttpFaultInjector {
  val rng = new Random

  override def faultChunk(httpChunk: HttpChunk) = {
    if (rng.nextFloat < rate)
      None
    else
      Some(httpChunk)
  }
}

object ContentLengthDroppingHttpFaultInjector extends HttpFaultInjector {
  override def faultMessage(httpMessage: HttpMessage) {
    httpMessage.removeHeader("Content-Length")
    httpMessage.removeHeader("Connection")
  }
}

class HttpFaultInjectorHandler(injectors: Tuple2[HttpFaultInjector, Double]*)
extends SimpleChannelDownstreamHandler {
  val faultCdf = {
    val cdf = injectors.foldLeft(Nil: List[Tuple2[Double, HttpFaultInjector]]) {
      case (Nil, (injector, freq))                   => (freq, injector) :: Nil
      case (cdf@((freq, _) :: _), (injector, freq_)) => (freq + freq_, injector) :: cdf
    }

    new Cdf(((1.0 -> NoHttpFaultInjector) :: cdf).reverse)
  }

  val faulter = faultCdf()

  override def writeRequested(ctx: ChannelHandlerContext, e: MessageEvent) {
    e.getMessage match {
      case httpChunk: HttpChunk =>
        // TODO: what do we do about futures in the case they get
        // filtered out?  We don't want the upstream to hang
        // indefinitely.
        for (newChunk <- faulter.faultChunk(httpChunk)) {
          val future = Channels.future(ctx.getChannel)
          future.addListener(new ChannelFutureListener {
            def operationComplete(future: ChannelFuture) {
              if (future.isSuccess)
                e.getFuture.setSuccess()
              else
                e.getFuture.setFailure(future.getCause)
            }
          })

          Channels.write(ctx, future, newChunk)
        }

      case httpMessage: HttpMessage =>
        faulter.faultMessage(httpMessage)
        ctx.sendDownstream(e)

      case _ =>
        ctx.sendDownstream(e)
    }
  }
}
