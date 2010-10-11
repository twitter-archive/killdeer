package com.twitter.killdeer

import scala.util.Random

import com.twitter.util.TimeConversions._
import com.twitter.util.StorageUnitConversions._
import com.twitter.util.Timer
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.buffer.ChannelBuffers
import java.util.concurrent.Executors
import java.net.InetSocketAddress
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.{ChannelPipeline, ChannelPipelineFactory}
import org.jboss.netty.handler.codec.http.{HttpChunkAggregator, HttpRequestDecoder, HttpResponseEncoder}
import org.jboss.netty.handler.stream.ChunkedWriteHandler
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpHeaders._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion._

object PickFromCDF {
  // We assume the CDF is well-formed.
  val rng = new Random
  def apply[T](cdf: Seq[Tuple2[Double, T]]):T = {
    val pctPick = rng.nextFloat
    val Some((_, value)) = cdf.find { case (pct, _) => pctPick < pct }
    value
  }
}

object Killdeer {
  def strToCDF(str: String) =
    for {
      pctile <- str.split(",")
      Array(pct, x) = pctile.split(":")
    } yield (pct.toDouble, x.toInt)

  def main(args: Array[String]) {
    val responseSource = args match {
      case Array(responseSizeCDF_, delayCDF_) =>
        // TODO: interpolate?
        val responseSizeCDF = strToCDF(responseSizeCDF_)
        val delayCDF = strToCDF(delayCDF_)
        new ResponseSource {
          def apply(txid: String) = {
            val length = PickFromCDF(responseSizeCDF)
            val delay = PickFromCDF(delayCDF)
            val a = new Array[Byte](length)
            java.util.Arrays.fill(a, '.'.toByte)
            RecordedResponse(ChannelBuffers.wrappedBuffer(a), Nil, delay)
          }
        }

      case Array(responseSampleDirectory) =>
        new SampleLoader(responseSampleDirectory)

      case _ =>
        throw new IllegalArgumentException("bad usage! (read the source code)")
    }
    
    val port = 6666
    val bootstrap = new ServerBootstrap(
      new NioServerSocketChannelFactory(
        Executors.newCachedThreadPool(),
        Executors.newCachedThreadPool()))
    bootstrap.setPipelineFactory(new ResponseSamplePipelineFactory(responseSource))
    bootstrap.bind(new InetSocketAddress(port))
  }
}

class PipelineListener extends SimpleChannelHandler {
  override def handleUpstream(ctx: ChannelHandlerContext, e: ChannelEvent) {
    println("UPSTREAM: %s".format(e))
    super.handleUpstream(ctx, e)
  }

  override def handleDownstream(ctx: ChannelHandlerContext, e: ChannelEvent) {
    println("DOWNSTREAM: %s".format(e))
    super.handleDownstream(ctx, e)
  }
}

class ResponseSamplePipelineFactory(responseSource: ResponseSource) extends ChannelPipelineFactory {
  val timer = new Timer

  def getPipeline = {
    val pipeline = Channels.pipeline()
    pipeline.addLast("faultInjector", new UpstreamFaultInjectorHandler(
      new TimeoutFaultInjector              -> 0.005,
      new ConnectionDisconnectFaultInjector -> 0.1
    ))
    // pipeline.addLast("listener", new PipelineListener)
    pipeline.addLast("decoder", new HttpRequestDecoder)
    pipeline.addLast("aggregator", new HttpChunkAggregator(64.kilobytes.inBytes.toInt))
    pipeline.addLast("encoder", new HttpResponseEncoder)

    pipeline.addLast("handler", new ResponseSampleHandler(timer, responseSource))
    pipeline
  }
}

class ResponseSampleHandler(timer: Timer, responseSource: ResponseSource)
extends SimpleChannelUpstreamHandler
{
  val HOP_BY_HOP_HEADERS = collection.mutable.HashSet(
    "Proxy-Connection",
    "Keep-Alive",
    "Transfer-Encoding",
    "TE",
    "Trailer",
    "Proxy-Authorization",
    "Proxy-Authenticate",
    "Upgrade",
    "Content-Length",
    "Connection")

  def txnid(request: HttpRequest) = request.getHeader("X-Transaction") match {
    case null => "-"
    case s => s
  }
  
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage.asInstanceOf[HttpRequest]
    val transactionId = txnid(request)
    val recordedResponse = responseSource(transactionId)
    val response = new DefaultHttpResponse(HTTP_1_1, OK)
    val channel = e.getChannel
    recordedResponse.headers.foreach { case (headerName, headerValue) =>
      if (!HOP_BY_HOP_HEADERS.contains(headerName)) response.addHeader(headerName, headerValue)
    }

    response.addHeader("Content-Length", recordedResponse.body.readableBytes)
    response.setContent(recordedResponse.body)
    timer.schedule(recordedResponse.latency.millis.fromNow) {
      val writeFuture = channel.write(response)
      if (!HttpHeaders.isKeepAlive(request)) {
        writeFuture.addListener(ChannelFutureListener.CLOSE)
      }
    }
  }
}
