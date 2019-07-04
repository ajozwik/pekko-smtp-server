# akka-smtp-server

[![Build Status](https://travis-ci.org/ajozwik/akka-smtp-server.svg?branch=master)](https://travis-ci.org/ajozwik/akka-smtp-server)
[![Coverage Status](https://coveralls.io/repos/github/ajozwik/akka-smtp-server/badge.svg?branch=master)](https://coveralls.io/github/ajozwik/akka-smtp-server?branch=master)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/4c70d8b812914b44ab7f398a49c1c533)](https://www.codacy.com/app/ajozwik/akka-smtp-server?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=ajozwik/akka-smtp-server&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/4c70d8b812914b44ab7f398a49c1c533)](https://www.codacy.com/app/ajozwik/akka-smtp-server?utm_source=github.com&utm_medium=referral&utm_content=ajozwik/akka-smtp-server&utm_campaign=Badge_Coverage)
Smtp server based on akka stream.

For minimal usage you need to provide `consumer` method with signature (Mail=>Future[ConsumedResult]).
`consumer` method receives [Mail](/smtp-util/src/main/scala/pl/jozwik/smtp/util/Mail.scala) object and it repeats with Future[SuccessfulConsumed] or Future[FailedConsumed].

[AddressHandler.scala](/akka-smtp/src/main/scala/pl/jozwik/smtp/server/AddressHandler.scala) is optional implementation for fail fast address resolution (blacklist).

Usage:
Implement trait [Consumer](/akka-smtp/src/main/scala/pl/jozwik/smtp/server/consumer/Consumer.scala)

Example implementation:
[LogConsumer](/akka-smtp/src/main/scala/pl/jozwik/smtp/server/consumer/LogConsumer.scala)

Example usage:

 - Pack project
> sbt pack
 - Provide [Consumer](/akka-smtp/src/main/scala/pl/jozwik/smtp/server/consumer/Consumer.scala) implementation ([FileLogConsumer](/akka-smtp/src/main/scala/pl/jozwik/smtp/server/consumer/FileLogConsumer.scala) in example)
> akka-smtp/target/pack/bin/main -Dconsumer.class=pl.jozwik.smtp.server.consumer.FileLogConsumer

 - or use project as dependency and provide own Main class 