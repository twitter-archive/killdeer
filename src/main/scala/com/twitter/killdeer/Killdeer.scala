package com.twitter.killdeer

import com.twitter.util.StorageUnitConversions._
import com.twitter.util.{Timer, Eval}
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.io.File
import java.util.concurrent.Executors
import java.net.InetSocketAddress
import org.jboss.netty.channel.{ChannelPipeline, ChannelPipelineFactory}
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.socket.ClientSocketChannelFactory
import org.jboss.netty.handler.stream.ChunkedWriteHandler

trait Config {
  def executor = Executors.newCachedThreadPool()
  def port = 6666
  def pipeline: ChannelPipeline
}

object Killdeer {
  def main(args: Array[String]) {
    val configFileName = args(0)
    val config = Eval[Config](new File(configFileName))
    val port = config.port
    val bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(config.executor, config.executor))

    bootstrap.setPipelineFactory(new ChannelPipelineFactory {
      def getPipeline = config.pipeline
    })
    bootstrap.bind(new InetSocketAddress(port))
  }
}