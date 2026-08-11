package pl.jozwik.smtp.util

trait WithTagged {
  protected def tagged(role: String): String = s"$role[${getClass.getSimpleName}]"
}
