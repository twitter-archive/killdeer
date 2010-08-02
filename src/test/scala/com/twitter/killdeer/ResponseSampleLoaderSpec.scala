package com.twitter.killdeer

import org.specs._

class ResponseSampleLoaderSpec extends Specification {
  var responseSample: ResponseSampleLoader = _

  "ResponseSampleLoader" should {
    doBefore {
      responseSample = new ResponseSampleLoader("src/test/resources/response-sample.txt")
    }

    "load responses from a file" in {
      responseSample.next() mustEqual Response(100, 100, 100)
    }

    "cycles through all responses" in {
      responseSample.next() mustEqual Response(100, 100, 100)
      responseSample.next() mustEqual Response(200, 200, 200)
      responseSample.next() mustEqual Response(300, 300, 300)
      responseSample.next() mustEqual Response(100, 100, 100)
    }
  }
}