package pl.jozwik.smtp.tls

import java.util.concurrent.locks.{Condition, Lock}
import java.util.concurrent.{TimeUnit, TimeoutException}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object UtilsHelper {

  private val DefaultAwaitTimeout: FiniteDuration = 20.seconds

  def await[T](lock: Lock, condition: Condition, timeout: FiniteDuration = DefaultAwaitTimeout)(f: => T): T =
    handleLock(lock, condition, awaitOrTimeout(timeout))(f)

  def signal[T](lock: Lock, condition: Condition)(f: => T): T =
    handleLock(lock, condition, _.signal())(f)

  private def awaitOrTimeout(timeout: FiniteDuration)(condition: Condition): Unit =
    if (!condition.await(timeout.toMillis, TimeUnit.MILLISECONDS)) {
      throw new TimeoutException(s"Timed out after $timeout waiting for signal")
    }

  private def handleLock[T](lock: Lock, condition: Condition, c: Condition => Unit)(f: => T): T =
    if (lock.tryLock()) {
      try {
        val r = f
        c(condition)
        r
      } finally {
        lock.unlock()
      }
    } else {
      f
    }

}
