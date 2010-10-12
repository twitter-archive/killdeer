import com.twitter.killdeer._
import org.jboss.netty.util.HashedWheelTimer
import org.jboss.netty.channel.Channels
import java.util.concurrent.TimeUnit
import org.jboss.netty.handler.codec.http.{HttpChunkAggregator, HttpRequestDecoder, HttpResponseEncoder}
import java.net.InetSocketAddress

new Config {
  val timer = new HashedWheelTimer(1, TimeUnit.MILLISECONDS)
  val contentLengthCdf = Cdf("0.5:500,1.0:1000")
  val latencyCdf       = Cdf("0.5:001,1.0:1000")

  def pipeline = {
    val pipeline = Channels.pipeline()

    pipeline.addLast("decoder",          new HttpRequestDecoder)
    pipeline.addLast("encoder",          new HttpResponseEncoder)
    pipeline.addLast("faults",           new UpstreamFaultInjectorHandler(
      new TimeoutFaultInjector              -> 0.005,
      new ConnectionDisconnectFaultInjector -> 0.1
    ))
    pipeline.addLast("latency",          new LatencyHandler(timer, latencyCdf))
    pipeline.addLast("dots",             new DotsHandler(contentLengthCdf))
    pipeline
  }
}
