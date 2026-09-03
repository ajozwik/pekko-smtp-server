package pl.jozwik.smtp.util

import scala.util.Properties

final case class RuntimeConstant(name: String, description: String) {
  def valueOrDefault[T](defaultValue: T): String = Properties.propOrElse(name, defaultValue.toString)
  def propOrNone: Option[String]                 = Properties.propOrNone(name)

  def asString: String =
    s"$name - $description"

}

object RuntimeConstants {
  private val prefix       = "smtp.tls"
  private val prefixClient = s"$prefix.client"

  val portKey: RuntimeConstant                = RuntimeConstant("smtp.port", "port to connect to")
  val sizeKey: RuntimeConstant                = RuntimeConstant("smtp.size", "max size of message in bytes")
  val consumerClass: RuntimeConstant          = RuntimeConstant("consumer.class", "class to consume messages")
  val clientKeyStorePassword: RuntimeConstant = RuntimeConstant(s"$prefixClient.keyStorePassword", "password for client key store")
  val tlsProtocol: RuntimeConstant            = RuntimeConstant(s"$prefix.protocol", "TLS protocol")
  val keyStorePassword: RuntimeConstant       = RuntimeConstant(s"$prefix.keyStorePassword", "password for key store")
  val keyStoreFile: RuntimeConstant           = RuntimeConstant(s"$prefix.keyStoreFile", "path to key store")
  val keyStoreResource: RuntimeConstant       = RuntimeConstant(s"$prefix.keyStoreResource", "path to key store resource")
  val trustStorePassword: RuntimeConstant     = RuntimeConstant(s"$prefix.trustStorePassword", "password for trust store")
  val trustStoreFile: RuntimeConstant         = RuntimeConstant(s"$prefix.trustStoreFile", "path to trust store")
  val trustStoreResource: RuntimeConstant     = RuntimeConstant(s"$prefix.trustStoreResource", "path to trust store resource")
  val ephemeral: RuntimeConstant              = RuntimeConstant(s"$prefix.ephemeral", "generate a throwaway self-signed key/trust store")

}
