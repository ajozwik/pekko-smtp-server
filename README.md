# akka-smtp-server

[![Build Status](https://travis-ci.org/ajozwik/akka-smtp-server.svg?branch=master)](https://travis-ci.org/ajozwik/akka-smtp-server)
[![Coverage Status](https://coveralls.io/repos/github/ajozwik/akka-smtp-server/badge.svg?branch=master)](https://coveralls.io/github/ajozwik/akka-smtp-server?branch=master)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/4c70d8b812914b44ab7f398a49c1c533)](https://www.codacy.com/app/ajozwik/akka-smtp-server?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=ajozwik/akka-smtp-server&amp;utm_campaign=Badge_Grade)

Smtp server based on akka stream.

For minimal usage you need to provide Consumer Actor.
Consumer Actor receives [Mail](/smtp-util/src/main/scala/pl/jozwik/smtp/util/Mail.scala) object and it repeats with SuccessfulConsumed or FailedConsumed.

[AddressHandler.scala](/akka-smtp/src/main/scala/pl/jozwik/smtp/server/AddressHandler.scala) is optional implementation for fail fast address resolution (blacklist).



Example of usage (with dummy consumer - just send to log mail object) in [Main.scala](/akka-smtp/src/main/scala/pl/jozwik/smtp/server/Main.scala)




