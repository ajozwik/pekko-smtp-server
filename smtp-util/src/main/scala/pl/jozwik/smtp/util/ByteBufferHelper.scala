package pl.jozwik.smtp.util

import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.util.ByteString

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

object ByteBufferHelper extends StrictLogging {

  val ReadOnlyBuffer: ByteBuffer = createEmptyBuffer.asReadOnlyBuffer()

  def referenceByteBuffer: AtomicReference[ByteBuffer] = new AtomicReference(ReadOnlyBuffer)

  def createEmptyBuffer: ByteBuffer       = createBuffer(0)
  def createBuffer(size: Int): ByteBuffer = ByteBuffer.allocate(size)

  def createBuffer(currentCapacity: Int, proposedCapacity: Int): ByteBuffer =
    if (proposedCapacity > currentCapacity) {
      createBuffer(proposedCapacity)
    } else {
      createBuffer(currentCapacity * 2)
    }

  def mergeAndFlip(a: ByteBuffer, b: ByteBuffer): ByteBuffer =
    merge(a, b).flip()

  def merge(a: ByteBuffer, b: ByteBuffer): ByteBuffer = {
    val capacity = a.remaining() + b.remaining()
    val buffer   = ByteBuffer.allocate(capacity)
    buffer.put(a).put(b)
  }

  def toByteBuffer(str: String, rest: String*): ByteBuffer = {
    val s = s"$str${rest.mkString}"
    ByteBuffer.wrap(s.getBytes(Constants.Utf8sCharset))
  }

  def split(buffer: ByteBuffer, at: Int): (ByteBuffer, ByteBuffer) = {
    val b1 = buffer.duplicate()
    b1.limit(at)
    val b2 = buffer.duplicate()
    b2.position(at)
    (b1.slice(), b2.slice())
  }

  def toByteString(message: ByteBuffer): ByteString =
    ByteString(message.flip())

  def toByteStringImmutable(message: ByteBuffer): ByteString =
    toByteString(message.duplicate())

  def copy(src: ByteBuffer, dst: AtomicReference[ByteBuffer]): Unit =
    dst.set(clone(src))

  def clone(src: ByteBuffer): ByteBuffer = {
    val size = src.remaining()
    val n    = ByteBuffer.allocate(size)
    n.put(src).flip()
  }

  def toByteBufferFlip(bytes: ByteString): ByteBuffer =
    bytes.toByteBuffer

}
