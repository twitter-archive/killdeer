import com.twitter.killdeer._
import org.jboss.netty.channel.Channels
import org.jboss.netty.handler.codec.http.{HttpChunkAggregator, HttpRequestDecoder, HttpResponseEncoder}
import java.net.InetSocketAddress

new Config {
  def pipeline = {
    val pipeline = Channels.pipeline()

    pipeline.addLast("decoder",          new HttpRequestDecoder)
    pipeline.addLast("encoder",          new HttpResponseEncoder)
    pipeline.addLast("returnRecorded",   new RecordedResponseHandler("~/.killdeer/responses"))
    pipeline
  }
}
