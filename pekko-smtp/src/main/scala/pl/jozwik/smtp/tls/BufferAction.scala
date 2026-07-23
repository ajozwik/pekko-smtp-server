package pl.jozwik.smtp.tls

import org.apache.pekko.util.ByteString

import java.nio.ByteBuffer

object BufferAction {

  def copyTo(srcByteString: ByteString)(dstBuffer: ByteBuffer): Int =
    copyTo(srcByteString.asByteBuffer)(dstBuffer)

  def copyTo(srcBuffer: ByteBuffer)(dstBuffer: ByteBuffer): Int = {
    val position = dstBuffer.position()
    dstBuffer.put(srcBuffer)
    dstBuffer.position() - position
  }

}
