package pl.jozwik.smtp.tls

import org.apache.pekko.util.ByteString

import java.nio.ByteBuffer

object BufferAction {

  def read(byteString: ByteString)(buffer: ByteBuffer): Int = {
    buffer.put(byteString.asByteBuffer)
    buffer.position()
  }

}
