package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.server.consumer.{ FileLogConsumer, LogConsumer }

class ServerFailFileSpec extends AbstractSmtpServerSpec(FileLogConsumer.consumer)

class ServerFailSpec extends AbstractSmtpServerSpec(LogConsumer.consumer)
