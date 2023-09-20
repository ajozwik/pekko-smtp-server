# pekko-smtp-server

[![Scala CI](https://github.com/ajozwik/pekko-smtp-server/actions/workflows/scala.yml/badge.svg)](https://github.com/ajozwik/pekko-smtp-server/actions/workflows/scala.yml)
[![Coverage Status](https://coveralls.io/repos/github/ajozwik/pekko-smtp-server/badge.svg?branch=master)](https://coveralls.io/github/ajozwik/pekko-smtp-server?branch=master)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/4c70d8b812914b44ab7f398a49c1c533)](https://www.codacy.com/app/ajozwik/pekko-smtp-server?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=ajozwik/pekko-smtp-server&amp;utm_campaign=Badge_Grade)
[![codecov](https://codecov.io/gh/ajozwik/pekko-smtp-server/graph/badge.svg?token=f5DwN4hYmt)](https://codecov.io/gh/ajozwik/pekko-smtp-server)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.ajozwik/pekko-smtp_2.13.svg?label=latest%20release%20for%202.13)](http://search.maven.org/#search|ga|1|g%3A%22com.github.ajozwik%22%20AND%20a%3A%22pekko-smtp_2.13%22)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.ajozwik/pekko-smtp_3.svg?label=latest%20release%20for%203)](http://search.maven.org/#search|ga|1|g%3A%22com.github.ajozwik%22%20AND%20a%3A%22pekko-smtp_3%22)

Smtp server based on pekko stream.

Add to your project:

```
 libraryDependencies += "com.github.ajozwik" %% "pekko-smtp" % <version>
```


For minimal usage you need to provide `consumer` method with signature (Mail=>Future[ConsumedResult]).
`consumer` method receives [Mail](/smtp-util/src/main/scala/pl/jozwik/smtp/util/Mail.scala) object and it repeats with Future[SuccessfulConsumed] or Future[FailedConsumed].

[AddressHandler.scala](/pekko-smtp/src/main/scala/pl/jozwik/smtp/server/AddressHandler.scala) is optional implementation for fail fast address resolution (blacklist).

Usage:
Implement trait [Consumer](/pekko-smtp/src/main/scala/pl/jozwik/smtp/server/consumer/Consumer.scala)

Example implementation:
[LogConsumer](/pekko-smtp/src/main/scala/pl/jozwik/smtp/server/consumer/LogConsumer.scala)

Example usage:

 - Pack project
> sbt pack
 - Provide [Consumer](/pekko-smtp/src/main/scala/pl/jozwik/smtp/server/consumer/Consumer.scala) implementation ([FileLogConsumer](/pekko-smtp/src/main/scala/pl/jozwik/smtp/server/consumer/FileLogConsumer.scala) in example)
> pekko-smtp/target/pack/bin/main -Dconsumer.class=pl.jozwik.smtp.server.consumer.FileLogConsumer

 - or use project as dependency and provide own Main class 
 
 
 If you know, how to handle STARTTLS with pekko-stream (pekko tcp) feel free to create a issue.
