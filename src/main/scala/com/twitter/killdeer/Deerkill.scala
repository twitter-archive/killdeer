package com.twitter.killdeer

import scala.io.Source
import scala.collection.mutable.Queue
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel._
import org.jboss.netty.util._
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.handler.codec.http._
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

object Deerkill {
  def apply(args: Seq[String]) {
    val host = args(0)
    val port = args(1).toInt
    val address = new InetSocketAddress(host, port)
    val concurrency = args(2).toInt
    val timeBetweenRequestMillis = args(3).toInt
    val timer = new HashedWheelTimer
    val requests = Source.fromInputStream(System.in).getLines map { requestLine =>
      val recordedRequest = RecordedRequest(requestLine)
      val httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, recordedRequest.uri)
      httpRequest.addHeader("Authorization", "UserId " + recordedRequest.userId)
      httpRequest.addHeader("X-Transaction", recordedRequest.transactionId)
      httpRequest.addHeader("Host", host)
      httpRequest
    }
    val queue = new collection.mutable.SynchronizedQueue[HttpRequest]
    queue ++= requests

    val executor = Executors.newCachedThreadPool()
    val socketChannelFactory = new NioClientSocketChannelFactory(executor, executor)
    val bootstrap = new ClientBootstrap(socketChannelFactory)
    bootstrap.setPipelineFactory(new ChannelPipelineFactory {
      def getPipeline = {
        val pipeline = Channels.pipeline()
        pipeline.addLast("decoder", new HttpResponseDecoder)
        pipeline.addLast("encoder", new HttpRequestEncoder)
        pipeline.addLast("outbound", new OutboundHandler(bootstrap, address, timer, queue))
        pipeline
      }
    })
    (0 until concurrency) foreach { _ =>
      bootstrap.connect(address)
    }
  }
}

class OutboundHandler(bootstrap: ClientBootstrap, address: InetSocketAddress, timer: Timer, requests: Queue[HttpRequest]) extends SimpleChannelUpstreamHandler {
  var channel: Channel = null

  override def channelConnected(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    channel = e.getChannel
    start()
  }

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {}

  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    bootstrap.connect(address)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    e.getCause().printStackTrace()
    bootstrap.connect(address)
  }

  private def start() {
    timer.newTimeout(new TimerTask {
      def run(timeout: Timeout) {
        val request = requests.dequeue()
        channel.write(request)
        start()
      }
    }, 100, TimeUnit.MILLISECONDS)
  }
}