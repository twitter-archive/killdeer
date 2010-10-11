package com.twitter.killdeer

import scala.collection.immutable.IndexedSeq
import scala.io.Source
import java.io.File
import com.twitter.util.Timer
import com.twitter.util.TimeConversions._
import scala.collection.mutable.ArrayBuffer
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpHeaders._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.jboss.netty.util.CharsetUtil

class RecordedResponseHandler(responseSampleDirectory: String) extends SimpleChannelHandler {
  import RecordedResponse.load

  def txnid(request: HttpRequest) = request.getHeader("X-Transaction") match {
    case null => "-"
    case s => s
  }
  
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage.asInstanceOf[HttpRequest]
    val channel = e.getChannel
    val transactionId = txnid(request)
    load(responseSampleDirectory, transactionId, request.getUri) map { recordedResponse =>
      channel.write(recordedResponse).addListener(new ChannelFutureListener {
        def operationComplete(future: ChannelFuture) {
          recordedResponse.releaseExternalResources()
        }
      })
    } getOrElse {
      ctx.sendUpstream(e)
    }
  }
}
