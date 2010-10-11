package com.twitter.killdeer

import java.net.InetSocketAddress
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.ClientSocketChannelFactory
import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.handler.codec.http.HttpRequestEncoder

class ProxyHandler(inetSocketAddress: InetSocketAddress, socketChannelFactory: ClientSocketChannelFactory) extends SimpleChannelUpstreamHandler {
  var inboundChannel: Channel = null
  var outboundChannel: Channel = null

  override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    def connect() = {
      val bootstrap = new ClientBootstrap(socketChannelFactory)
      val pipeline = bootstrap.getPipeline
      pipeline.addLast("encoder", new HttpRequestEncoder)
      pipeline.addLast("handler", new OutboundHandler(e.getChannel))
      bootstrap.connect(inetSocketAddress)
    }

    def suspendTrafficWhileConnecting() {
      inboundChannel.setReadable(false)
    }

    inboundChannel = e.getChannel
    suspendTrafficWhileConnecting()
    val f = connect()
    outboundChannel = f.getChannel
    f.addListener(new ChannelFutureListener {
      def operationComplete(future: ChannelFuture) {
        if (future.isSuccess()) {
          inboundChannel.setReadable(true)
        } else {
          inboundChannel.close()
        }
      }
    })
  }

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val msg = e.getMessage
    outboundChannel.write(msg).addListener(new ChannelFutureListener {
      def operationComplete(future: ChannelFuture) {
        e.getChannel.close()
      }
    })
  }
  
  private class OutboundHandler(inboundChannel: Channel) extends SimpleChannelUpstreamHandler {
    override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
      inboundChannel.write(e.getMessage)
    }

    override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
      
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
      
    }
  }
}
