package pl.jozwik.smtp.tls

import com.typesafe.scalalogging.StrictLogging

import java.io.{File, FileInputStream, InputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.Callable
import scala.util.Properties
import scala.jdk.CollectionConverters.*

// Generates a throwaway self-signed keystore/truststore via the JDK's own keytool, once per JVM.
// Used by tests so they don't depend on TLS material committed to the repository, and available
// as an opt-in fallback for production (see Main's smtp.tls.ephemeral flag) when no real
// certificate is configured - the key is never persisted and is not suitable for real traffic.
object EphemeralTls extends StrictLogging {

  private val ServerAlias = "server"
  private val ClientAlias = "client"

  private val directory: File = {
    logger.warn("Generating an ephemeral self-signed TLS keystore/truststore - not suitable for production traffic.")
    val dir = Files.createTempDirectory("smtp-tls").toFile
    dir.deleteOnExit()
    dir
  }

  val serverKeyStoreFile: File = generateKeyStore(ServerAlias, TlsOpts.keystorePassword, "CN=localhost")
  val clientKeyStoreFile: File = generateKeyStore(ClientAlias, TlsOpts.clientKeystorePassword, "CN=smtp-client")

  val trustStoreFile: File = {
    val certificate = new File(directory, s"$ServerAlias.cer")
    runKeytool(
      "-exportcert",
      "-alias",
      ServerAlias,
      "-keystore",
      serverKeyStoreFile.getAbsolutePath,
      "-storetype",
      "PKCS12",
      "-storepass",
      TlsOpts.keystorePassword,
      "-file",
      certificate.getAbsolutePath,
      "-rfc"
    )
    val trustStore = new File(directory, "trustedCerts.jks")
    runKeytool(
      "-importcert",
      "-alias",
      ServerAlias,
      "-keystore",
      trustStore.getAbsolutePath,
      "-storetype",
      "JKS",
      "-storepass",
      TlsOpts.trustPassword,
      "-file",
      certificate.getAbsolutePath,
      "-noprompt"
    )
    trustStore.deleteOnExit()
    trustStore
  }

  def serverTlsOpts: TlsOpts = TlsOpts(
    inputStream(serverKeyStoreFile),
    TlsOpts.keystorePassword,
    TlsOpts.keyPassword,
    inputStream(trustStoreFile),
    TlsOpts.trustPassword
  )

  def clientTlsOpts: TlsOpts = TlsOpts(
    inputStream(clientKeyStoreFile),
    TlsOpts.clientKeystorePassword,
    TlsOpts.clientKeystorePassword,
    inputStream(trustStoreFile),
    TlsOpts.trustPassword
  )

  private def inputStream(file: File): Callable[InputStream] = () => new FileInputStream(file)

  private def generateKeyStore(alias: String, password: String, distinguishedName: String): File = {
    val keyStore = new File(directory, s"$alias.p12")
    runKeytool(
      "-genkeypair",
      "-alias",
      alias,
      "-keyalg",
      "RSA",
      "-keysize",
      "2048",
      "-validity",
      "3650",
      "-storetype",
      "PKCS12",
      "-keystore",
      keyStore.getAbsolutePath,
      "-storepass",
      password,
      "-keypass",
      password,
      "-dname",
      distinguishedName,
      "-ext",
      "SAN=dns:localhost,ip:127.0.0.1"
    )
    keyStore.deleteOnExit()
    keyStore
  }

  private def runKeytool(args: String*): Unit = {
    val keytool = new File(new File(Properties.javaHome, "bin"), "keytool").getAbsolutePath
    val process = new ProcessBuilder((keytool :: args.toList).asJava).redirectErrorStream(true).start()
    val output  = new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
    val exit    = process.waitFor()
    if (exit != 0) {
      throw new IllegalStateException(s"keytool ${args.mkString(" ")} failed with exit code $exit: $output")
    }
    logger.trace(output)
  }

}
