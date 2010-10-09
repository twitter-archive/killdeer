package com.twitter.killdeer

import com.twitter.util.StorageUnitConversions._
import com.twitter.util.Timer
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.util.concurrent.Executors
import java.net.InetSocketAddress
import org.jboss.netty.channel.{ChannelPipeline, ChannelPipelineFactory}
import org.jboss.netty.channel.Channels
import org.jboss.netty.handler.codec.http.{HttpChunkAggregator, HttpRequestDecoder, HttpResponseEncoder}
import org.jboss.netty.channel.socket.ClientSocketChannelFactory
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory

object Killdeer {
  def main(args: Array[String]) {
    val responseSampleDirectory = args(0)
    val port = 6666
    val executor = Executors.newCachedThreadPool()
    val bootstrap = new ServerBootstrap(
      new NioServerSocketChannelFactory(executor, executor))
    val clientSocketChannelFactory = new NioClientSocketChannelFactory(executor, executor)

    bootstrap.setPipelineFactory(new KilldeerPipelineFactory(responseSampleDirectory, clientSocketChannelFactory))
    bootstrap.bind(new InetSocketAddress(port))
  }
}

class KilldeerPipelineFactory(responseSampleDirectory: String, clientSocketChannelFactory: ClientSocketChannelFactory) extends ChannelPipelineFactory {
  val timer = new Timer

  def getPipeline = {
    val pipeline = Channels.pipeline()
    pipeline.addLast("decoder",          new HttpRequestDecoder)
    pipeline.addLast("encoder",          new HttpResponseEncoder)

    pipeline.addLast("return_recorded",  new RecordedResponseHandler(timer, responseSampleDirectory))
    pipeline.addLast("record_returned",  new ResponseRecorderHandler(responseSampleDirectory))
    pipeline.addLast("proxy",            new Proxy(new InetSocketAddress("localhost", 80), clientSocketChannelFactory))
    pipeline
  }
}