package pl.jozwik.smtp.util

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

object ByteBufferHelper {

  val ReadOnlyBuffer: ByteBuffer = ByteBuffer.allocate(0).asReadOnlyBuffer()

  def createBuffer(currentCapacity: Int, proposedCapacity: Int): ByteBuffer =
    if (proposedCapacity > currentCapacity) {
      ByteBuffer.allocate(proposedCapacity)
    } else {
      ByteBuffer.allocate(currentCapacity * 2)
    }

  def handleBufferUnderflow(bufferSize: Int, buffer: AtomicReference[ByteBuffer]): Unit =
    if (bufferSize >= buffer.get.limit) {
      val replaceBuffer = createBuffer(buffer.get.capacity(), bufferSize)
      buffer.get.flip()
      replaceBuffer.put(buffer.get)
      buffer.set(replaceBuffer)
    }

  def toByteBuffer(str: String): ByteBuffer = ByteBuffer.wrap(str.getBytes(Constants.Utf8sCharset))

  def toString(byteBuffer: ByteBuffer): String =
    if (byteBuffer.isReadOnly) {
      ""
    } else {
      new String(byteBuffer.array().takeWhile(_ != 0), Constants.Utf8sCharset).trim()
    }

}
