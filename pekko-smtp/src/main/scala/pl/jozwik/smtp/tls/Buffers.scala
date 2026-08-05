package pl.jozwik.smtp.tls

import pl.jozwik.smtp.util.ByteBufferHelper

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

object Buffers {

  def apply(appBufferSize: Int, packetBufferSize: Int): Buffers =
    Buffers(
      new AtomicReference(ByteBuffer.allocate(packetBufferSize)),
      new AtomicReference(ByteBuffer.allocate(packetBufferSize)),
      ByteBuffer.allocate(appBufferSize),
      new AtomicReference(ByteBuffer.allocate(appBufferSize))
    )

  def empty: Buffers = Buffers(0, 0)

}

final case class Buffers(
    peerNetData: AtomicReference[ByteBuffer],
    myNetData: AtomicReference[ByteBuffer],
    myAppDataLocal: ByteBuffer,
    peerAppDataLocal: AtomicReference[ByteBuffer],
    underflowBuffer: AtomicReference[ByteBuffer] = ByteBufferHelper.referenceByteBuffer
) {

  def clear: Buffers = Buffers(myAppDataLocal.capacity(), peerAppDataLocal.get.capacity())
}
