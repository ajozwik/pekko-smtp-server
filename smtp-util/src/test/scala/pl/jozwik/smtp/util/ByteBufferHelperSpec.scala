package pl.jozwik.smtp.util

import org.apache.pekko.util.ByteString

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class ByteBufferHelperSpec extends AbstractSpec {

  "ByteBufferHelper" should {
    "copy buffers " in {
      val src    = "aa"
      val source = ByteBuffer.allocate(src.length * 2)
      source.position(src.length).put(src.getBytes(StandardCharsets.UTF_8)).position(src.length)
      val sourceDuplicate = source.duplicate()
      val result          = ByteBufferHelper.clone(source)
      ByteString(result) shouldBe ByteString(sourceDuplicate)
    }
  }

}
