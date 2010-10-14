import com.twitter.killdeer._
import org.jboss.netty.channel.Channels
import org.jboss.netty.handler.codec.http.{HttpChunkAggregator, HttpRequestDecoder, HttpResponseEncoder}
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import java.net.InetSocketAddress
import java.util.concurrent.Executors

new Config {
  val executor = Executors.newCachedThreadPool()
  val clientSocketChannelFactory = new NioClientSocketChannelFactory(executor, executor)
  
  def pipeline = {
    val pipeline = Channels.pipeline()

    pipeline.addLast("decoder",          new HttpRequestDecoder)
    pipeline.addLast("encoder",          new HttpResponseEncoder)

    pipeline.addLast("log",              new LoggingHandler)
    pipeline.addLast("recordReturned",   new ResponseRecorderHandler(System.getProperty("user.home") + "/.killdeer/responses/"))
    pipeline.addLast("proxy",            new ProxyHandler(new InetSocketAddress("localhost", 80), clientSocketChannelFactory))
    pipeline
  }
}
