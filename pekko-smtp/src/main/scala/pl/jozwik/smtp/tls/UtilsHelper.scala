package pl.jozwik.smtp.tls

import java.util.concurrent.locks.{Condition, Lock}

object UtilsHelper {

  def await[T](lock: Lock, condition: Condition)(f: => T): T = handleLock(lock, condition, _.await)(f)

  def signal[T](lock: Lock, condition: Condition)(f: => T): T =
    handleLock(lock, condition, _.signal)(f)

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
