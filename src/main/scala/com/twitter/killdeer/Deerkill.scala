package com.twitter.killdeer

import scala.io.Source
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.handler.codec.http._
import java.net.InetSocketAddress

object Deerkill {
  def apply(args: Seq[String]) {
    val host = args(0)
    val port = args(1).toInt
    val requests = Source.fromInputStream(System.in).getLines map { requestLine =>
      val recordedRequest = RecordedRequest(requestLine)
      val httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, recordedRequest.uri)
      httpRequest.addHeader("Authorization", "UserId " + recordedRequest.userId)
      httpRequest.addHeader("X-Transaction", recordedRequest.transactionId)
      httpRequest.addHeader("Host", host)
      httpRequest
    }
    val executor = Executors.newFixedThreadPool(5)
    val socketChannelFactory = new NioClientSocketChannelFactory(executor, executor)
    val bootstrap = new ClientBootstrap(socketChannelFactory)
    bootstrap.setPipelineFactory(new ChannelPipelineFactory {
      def getPipeline = {
        val pipeline = Channels.pipeline()
        pipeline.addLast("decoder", new HttpResponseDecoder)
        pipeline.addLast("encoder", new HttpRequestEncoder)
        pipeline.addLast("outbound", new OutboundHandler)
        pipeline
      }
    })
    doIt(bootstrap, new InetSocketAddress(host, port), requests)
  }
  
  private def doIt(bootstrap: ClientBootstrap, address: InetSocketAddress, requests: Iterator[HttpRequest]) {
    if (requests.hasNext) {
      val httpRequest = requests.next()
      val connection = bootstrap.connect(address)
      connection.addListener(new ChannelFutureListener {
        def operationComplete(future: ChannelFuture) {
          val outboundChannel = connection.getChannel
          println("writing " + httpRequest)
          outboundChannel.write(httpRequest).addListener(new ChannelFutureListener {
            def operationComplete(future: ChannelFuture) {
              if (future.isSuccess) doIt(bootstrap, address, requests)
              else println("error")
            }
          })
        }
      })
    }
  }
}

class OutboundHandler extends SimpleChannelUpstreamHandler {
  override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    // e.getChannel.write(request.next())
  }

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    e.getMessage match {
      case msg: HttpMessage if !msg.isChunked => e.getChannel.close()
      case msg: HttpChunk if msg.isLast => e.getChannel.close()
      case e =>
    }
  }

  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    // do something
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    e.getCause().printStackTrace()
  }
}