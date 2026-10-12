package miniledger

import cats.effect.IO
import com.comcast.ip4s.{Host, Port}
import pureconfig.*

final case class HttpConfig(host: Host, port: Port)
final case class LedgerConfig(queueCapacity: Int, mintingAccounts: Set[AccountId])
final case class AppConfig(http: HttpConfig, ledger: LedgerConfig)

object AppConfig:
  given ConfigReader[Host] =
    ConfigReader.fromStringOpt(s => Host.fromString(s))
  given ConfigReader[Port] =
    ConfigReader.fromStringOpt(s => Port.fromString(s))
  given ConfigReader[HttpConfig] = ConfigReader.forProduct2("host", "port")(HttpConfig.apply)
  given ConfigReader[LedgerConfig] =
    ConfigReader.forProduct2("queue-capacity", "minting-accounts")(LedgerConfig.apply)
  given ConfigReader[AppConfig] = ConfigReader.forProduct2("http", "ledger")(AppConfig.apply)

  def load: IO[AppConfig] = IO.delay(ConfigSource.default.loadOrThrow[AppConfig])
