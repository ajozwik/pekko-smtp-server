package pl.jozwik.smtp.tls

trait WithSequenceIterator {
  protected val iterator: Iterator[Int] = Iterator.from(0)
}
