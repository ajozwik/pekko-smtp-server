package pl.jozwik.smtp.util

import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.util.ByteString

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

object ByteBufferHelper extends StrictLogging {

  val ReadOnlyBuffer: ByteBuffer = createBuffer().asReadOnlyBuffer()

  def referenceByteBuffer: AtomicReference[ByteBuffer] = new AtomicReference(ReadOnlyBuffer)

  def createBuffer(size: Int = 0): ByteBuffer = ByteBuffer.allocate(size)

  def createBuffer(currentCapacity: Int, proposedCapacity: Int): ByteBuffer =
    if (proposedCapacity > currentCapacity) {
      createBuffer(proposedCapacity)
    } else {
      createBuffer(currentCapacity * 2)
    }

  val fakeRead: ByteBuffer => Int = _ => 0

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

  def clearBuffer(buffer: ByteBuffer): ByteBuffer = {
    val b = buffer.clear()
    logger.debug(s"$b")
    b
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
