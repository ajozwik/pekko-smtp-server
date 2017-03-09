# akka-smtp-server

[![Build Status](https://travis-ci.org/ajozwik/akka-smtp-server.svg?branch=master)](https://travis-ci.org/ajozwik/akka-smtp-server)
[![Coverage Status](https://coveralls.io/repos/github/ajozwik/akka-smtp-server/badge.svg?branch=master)](https://coveralls.io/github/ajozwik/akka-smtp-server?branch=master)
[![Codacy Badge](https://api.codacy.com/project/badge/grade/e604aa27b4ae4b5484225b31151c5e01)](https://www.codacy.com/app/ajozwik/akka-smtp-server)

Smtp server based on akka actors.

For minimal usage you need to provide Consumer Actor.
Consumer Actor receives [Mail](/smtp-util/src/main/scala/pl/jozwik/smtp/util/Mail.scala) object and it repeats with SuccessfulConsumed or FailedConsumed.

[AddressHandler.scala](/akka-smtp/src/main/scala/pl/jozwik/smtp/server/AddressHandler.scala) is optional implementation for fail fast address resolution (blacklist).



Example of usage (with dummy consumer - just send to log mail object) in [Main.scala](/akka-smtp/src/main/scala/pl/jozwik/smtp/server/Main.scala)




