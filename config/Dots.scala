import com.twitter.killdeer._
import org.jboss.netty.util.HashedWheelTimer
import org.jboss.netty.channel.Channels
import java.util.concurrent.TimeUnit
import org.jboss.netty.handler.codec.http.{HttpChunkAggregator, HttpRequestDecoder, HttpResponseEncoder}
import java.net.InetSocketAddress
import util.Random

new Config {
  val rng = new Random

  val contentLengthCdf = Cdf(
    0.5 -> 500,
    1.0 -> 1000
  )
  val latencyCdf = Cdf(
    0.5 -> 1,
    1.0 -> 1000
  )
  val timer = new HashedWheelTimer(1, TimeUnit.MILLISECONDS)

  val bandwidthCdf = Cdf(
    0.5  -> 10000,
    0.75 -> 1000,
    0.95 -> 100,
    1.0  -> 10
  )


  def pipeline = {
    val pipeline = Channels.pipeline()

    if (rng.nextFloat < .5)  // else: unlimited bandwidth
      pipeline.addLast("throttler", new DownstreamThrottlingHandler(bandwidthCdf(), timer))
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
