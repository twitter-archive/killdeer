import com.twitter.killdeer._
import org.jboss.netty.channel.Channels
import org.jboss.netty.util.HashedWheelTimer
import java.util.concurrent.TimeUnit
import org.jboss.netty.handler.codec.http.{HttpChunkAggregator, HttpRequestDecoder, HttpResponseEncoder}
import java.net.InetSocketAddress

new Config {
  val timer = new HashedWheelTimer(1, TimeUnit.MILLISECONDS)
  val latencyCdf = Cdf("0.5:001,1.0:1000")

  def pipeline = {
    val pipeline = Channels.pipeline()

    pipeline.addLast("decoder",          new HttpRequestDecoder)
    pipeline.addLast("encoder",          new HttpResponseEncoder)
    pipeline.addLast("latency",          new LatencyHandler(timer, latencyCdf))
    pipeline.addLast("returnRecorded",   new RecordedResponseHandler(System.getProperty("user.home") + "/.killdeer/responses/"))
    pipeline
  }
}
