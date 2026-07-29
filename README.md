# pekko-smtp-server

[![Scala CI](https://github.com/ajozwik/pekko-smtp-server/actions/workflows/scala.yml/badge.svg)](https://github.com/ajozwik/pekko-smtp-server/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/ajozwik/pekko-smtp-server/badge.svg?branch=master)](https://coveralls.io/github/ajozwik/pekko-smtp-server?branch=master)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/4c70d8b812914b44ab7f398a49c1c533)](https://www.codacy.com/app/ajozwik/pekko-smtp-server?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=ajozwik/pekko-smtp-server&amp;utm_campaign=Badge_Grade)
[![codecov](https://codecov.io/gh/ajozwik/pekko-smtp-server/graph/badge.svg?token=f5DwN4hYmt)](https://codecov.io/gh/ajozwik/pekko-smtp-server)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.ajozwik/pekko-smtp_2.13.svg?label=latest%20release%20for%202.13)](http://search.maven.org/#search|ga|1|g%3A%22com.github.ajozwik%22%20AND%20a%3A%22pekko-smtp_2.13%22)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.ajozwik/pekko-smtp_3.svg?label=latest%20release%20for%203)](http://search.maven.org/#search|ga|1|g%3A%22com.github.ajozwik%22%20AND%20a%3A%22pekko-smtp_3%22)

Smtp server based on pekko stream.

Add to your project:

```
 libraryDependencies += "com.github.ajozwik" %% "pekko-smtp" % "0.4.0"
```


For minimal usage you need to provide `consumer` method with signature (Mail=>Future[ConsumedResult]).
`consumer` method receives [Mail](smtp-util/src/main/scala/pl/jozwik/smtp/util/Mail.scala) object, and it repeats with Future[SuccessfulConsumed] or Future[FailedConsumed].

[AddressHandler.scala](pekko-smtp/src/main/scala/pl/jozwik/smtp/server/AddressHandler.scala) is an optional implementation for fail fast address resolution (blacklist).

Usage:
Implement trait [Consumer](pekko-smtp/src/main/scala/pl/jozwik/smtp/server/consumer/Consumer.scala)

#### Custom Consumer Implementation

```scala
import pl.jozwik.smtp.server.consumer.Consumer
import pl.jozwik.smtp.util.{ConsumedResult, Mail, SuccessfulConsumed}
import scala.concurrent.Future

class MyConsumer extends Consumer {
  override def consumer(mail: Mail): Future[ConsumedResult] = {
    // Process mail here
    Future.successful(SuccessfulConsumed)
  }
}
```

### Programmatic Usage

#### Minimal SMTP Server

```scala
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Tcp
import pl.jozwik.smtp.server.{ConnectionHandler, NopAddressHandler, StreamServer}
import pl.jozwik.smtp.util.{Mail, SuccessfulConsumed}
import scala.concurrent.Future
import scala.concurrent.duration.*

implicit val system: ActorSystem = ActorSystem("smtp-server")
val consumer = (mail: Mail) => {
  println(s"Received mail from ${mail.from} to ${mail.to}")
  Future.successful(SuccessfulConsumed)
}

val maxSize = 1024 * 1024 // 1MB
val readTimeout = 30.seconds
val connectionHandler = ConnectionHandler.connectionHandler(maxSize, consumer, readTimeout, NopAddressHandler)()(system)
val port = 2525
val server = StreamServer((host, port) => Tcp().bind(host, port), port)(connectionHandler)(system)

// To stop the server:
// server.close()
```

#### SMTP Client

```scala
import pl.jozwik.smtp.client.StreamClient
import pl.jozwik.smtp.util.{EmailWithContent, Mail, MailAddress, SocketAddress}

val address = SocketAddress("localhost", port)
val client = new StreamClient(address)

val mail = Mail(
  from = MailAddress("sender", "example.com"),
  to = Seq(MailAddress("recipient", "example.com")),
  emailContent = EmailWithContent.txtOnly(Seq.empty, Seq.empty, "Subject", "Hello World!")
)

client.sendMail(mail)
```

#### Custom Address Handler

You can implement `AddressHandler` to filter incoming or outgoing mail addresses.

```scala
import pl.jozwik.smtp.server.AddressHandler
import pl.jozwik.smtp.util.MailAddress

class BlacklistAddressHandler(blacklist: Set[String]) extends AddressHandler {
  override def acceptFrom(from: MailAddress): Boolean = !blacklist.contains(from.domain)
  override def acceptTo(to: MailAddress): Boolean = true
}
```

#### TLS Support

To enable TLS (STARTTLS), you need to provide `TlsOpts`.

```scala
import pl.jozwik.smtp.TlsOpts
import java.util.concurrent.Callable
import java.io.InputStream

// Example using classpath resources
val keyStoreStream: Callable[InputStream] = () => getClass.getResourceAsStream("/keystore.jks")
val trustStoreStream: Callable[InputStream] = () => getClass.getResourceAsStream("/truststore.jks")

val tlsOpts = TlsOpts(
  keyStoreInputStream = keyStoreStream,
  keystorePassword = "password",
  keyPassword = "password",
  trustStoreInputStream = trustStoreStream,
  trustPassword = "password"
)

// Server with TLS and Address Handler
val tlsConnectionHandler = ConnectionHandler.connectionHandler(
  maxSize,
  consumer,
  readTimeout,
  new BlacklistAddressHandler(Set("spam.com"))
)(Some(tlsOpts))(system)

// Client with TLS
val tlsClient = new StreamClient(address, tlsOpts)(system)
```

#### Sending Mail with Attachments

```scala
import pl.jozwik.smtp.util.Attachment
import org.apache.pekko.util.ByteString

val mailWithAttachment = Mail(
  from = MailAddress("sender", "example.com"),
  to = Seq(MailAddress("recipient", "example.com")),
  emailContent = EmailWithContent(
    from = Seq(MailAddress("sender", "example.com")),
    to = Seq(MailAddress("recipient", "example.com")),
    subject = Some("Report"),
    txtBody = Some("Please find the report attached."),
    htmlBody = None,
    attachments = Seq(Attachment("report.pdf", ByteString("...pdf content...")))
  )
)
```

Example implementation:
[LogConsumer](pekko-smtp/src/main/scala/pl/jozwik/smtp/server/consumer/LogConsumer.scala)

Example usage:

 - Pack project
> sbt pack
 - Provide [Consumer](pekko-smtp/src/main/scala/pl/jozwik/smtp/server/consumer/Consumer.scala) implementation ([FileLogConsumer](pekko-smtp/src/main/scala/pl/jozwik/smtp/server/consumer/FileLogConsumer.scala) in example)
> pekko-smtp/target/pack/bin/main -Dconsumer.class=pl.jozwik.smtp.server.consumer.FileLogConsumer

 - or use a project as a dependency and provide own Main class 

List of tls system properties (optional):

* smtp.port - port to connect to
* consumer.class - class to consume messages
* smtp.tls.ephemeral - generate a throwaway self-signed key/trust store instead of requiring one
* smtp.tls.keyStorePassword - password for key store
* smtp.tls.trustStorePassword - password for trust store
* smtp.tls.protocol - TLS protocol
* smtp.tls.keyStoreFile - path to key store
* smtp.tls.keyStoreResource - path to key store resource
* smtp.tls.trustStoreFile - path to trust store
* smtp.tls.trustStoreResource - path to trust store resource
* smtp.tls.client.keyStorePassword - password for client key store
* smtp.size - max size of message in bytes

Thanks to https://github.com/alexbokos/sslengine.example for example of tls/ssl implementation.

For build docs/README.md use:
```
docs/mdoc --out .
```