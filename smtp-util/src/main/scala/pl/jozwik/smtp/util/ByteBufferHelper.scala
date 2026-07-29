package pl.jozwik.smtp.util

import com.typesafe.scalalogging.StrictLogging

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

object ByteBufferHelper extends StrictLogging {

  val ReadOnlyBuffer: ByteBuffer = ByteBuffer.allocate(0).asReadOnlyBuffer()

  def referenceByteBuffer: AtomicReference[ByteBuffer] = new AtomicReference(ReadOnlyBuffer)

  def createBuffer(currentCapacity: Int, proposedCapacity: Int): ByteBuffer =
    if (proposedCapacity > currentCapacity) {
      ByteBuffer.allocate(proposedCapacity)
    } else {
      ByteBuffer.allocate(currentCapacity * 2)
    }

  val fakeRead: ByteBuffer => Int = _ => 0

  def merge(a: ByteBuffer, b: ByteBuffer): ByteBuffer = {
    val capacity = a.capacity() + b.capacity()
    val buffer   = ByteBuffer.allocate(capacity)
    try {
      buffer.put(a).put(b)
    } catch {
      case ex: Throwable =>
        logger.error("", ex)
        throw ex
    }
    buffer.flip()
  }

  def toByteBuffer(str: String): ByteBuffer = ByteBuffer.wrap(str.getBytes(Constants.Utf8sCharset))

  def toString(byteBuffer: ByteBuffer): String =
    if (byteBuffer.isReadOnly) {
      ""
    } else {
      new String(byteBuffer.array().takeWhile(_ != 0), Constants.Utf8sCharset).trim()
    }

  def split(buffer: ByteBuffer, at: Int): (ByteBuffer, ByteBuffer) = {
    val b1 = buffer.duplicate()
    b1.limit(at)
    val b2 = buffer.duplicate()
    b2.position(at)
    (b1.slice(), b2.slice())
  }

}
