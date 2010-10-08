package com.twitter.killdeer

import com.twitter.util.TimeConversions._
import com.twitter.util.StorageUnitConversions._
import com.twitter.util.Timer
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.util.concurrent.Executors
import java.net.InetSocketAddress
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.{ChannelPipeline, ChannelPipelineFactory}
import org.jboss.netty.handler.codec.http.{HttpChunkAggregator, HttpRequestDecoder, HttpResponseEncoder}
import org.jboss.netty.handler.stream.ChunkedWriteHandler
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpHeaders._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.jboss.netty.util.CharsetUtil

object Killdeer {
  def main(args: Array[String]) {
    val responseSampleDirectory = args(0)
    val port = 6666

    val bootstrap = new ServerBootstrap(
      new NioServerSocketChannelFactory(
        Executors.newCachedThreadPool(),
        Executors.newCachedThreadPool()))
    bootstrap.setPipelineFactory(new ResponseSamplePipelineFactory(responseSampleDirectory))
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

class ResponseSamplePipelineFactory(responseSampleDirectory: String) extends ChannelPipelineFactory {
  val timer = new Timer

  def getPipeline = {
    val pipeline = Channels.pipeline()
    pipeline.addLast("faultInjector", new UpstreamFaultInjectorHandler(
      new TimeoutFaultInjector              -> 0.005,
      new ConnectionDisconnectFaultInjector -> 0.001
    ))
    // pipeline.addLast("listener", new PipelineListener)
    pipeline.addLast("decoder", new HttpRequestDecoder)
    pipeline.addLast("aggregator", new HttpChunkAggregator(64.kilobytes.inBytes.toInt))
    pipeline.addLast("encoder", new HttpResponseEncoder)

    pipeline.addLast("handler", new ResponseSampleHandler(timer, responseSampleDirectory))
    pipeline
  }
}

class ResponseSampleHandler(timer: Timer, responseSampleDirectory: String) extends SimpleChannelUpstreamHandler {
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
  
  val sampleLoader = new SampleLoader(responseSampleDirectory)

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage.asInstanceOf[HttpRequest]
    val transactionId = txnid(request)
    val recordedResponse = sampleLoader(transactionId)
    val response = new DefaultHttpResponse(HTTP_1_1, OK)
    val channel = e.getChannel
    recordedResponse.headers.foreach { case (headerName, headerValue) =>
      if (!HOP_BY_HOP_HEADERS.contains(headerName)) response.addHeader(headerName, headerValue)
    }
    val body = recordedResponse.body + "\n"
    response.addHeader("Content-Length", body.size)
    response.setContent(ChannelBuffers.copiedBuffer(body, CharsetUtil.UTF_8))
    timer.schedule(recordedResponse.latency.millis.fromNow) {
      val writeFuture = channel.write(response)
      if (!HttpHeaders.isKeepAlive(request)) {
        writeFuture.addListener(ChannelFutureListener.CLOSE)
      }
    }
  }
}
