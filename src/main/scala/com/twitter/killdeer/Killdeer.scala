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


class ResponseSamplePipelineFactory(responseSampleDirectory: String) extends ChannelPipelineFactory {
  val timer = new Timer

  def getPipeline = {
    val pipeline = Channels.pipeline()
    pipeline.addLast("decoder", new HttpRequestDecoder)
    pipeline.addLast("aggregator", new HttpChunkAggregator(64.kilobytes.inBytes.toInt))
    pipeline.addLast("encoder", new HttpResponseEncoder)

    pipeline.addLast("handler", new ResponseSampleHandler(timer, responseSampleDirectory))
    pipeline
  }
}

class ResponseSampleHandler(timer: Timer, responseSampleDirectory: String) extends SimpleChannelUpstreamHandler {
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
      response.addHeader(headerName, headerValue)
    }
    response.setContent(ChannelBuffers.copiedBuffer(recordedResponse.body + "\n", CharsetUtil.UTF_8))
    timer.schedule(recordedResponse.latency.millis.fromNow) {
      val writeFuture = channel.write(response)
      if (!HttpHeaders.isKeepAlive(request)) {
        writeFuture.addListener(ChannelFutureListener.CLOSE)
      }
    }
  }
}
