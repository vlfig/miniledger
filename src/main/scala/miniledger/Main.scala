package miniledger

import cats.effect.{IO, IOApp}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    for
      cfg <- AppConfig.load
      _ <- Ledger
        .start(cfg.ledger)
        .flatMap(ledger =>
          EmberServerBuilder
            .default[IO]
            .withHost(cfg.http.host)
            .withPort(cfg.http.port)
            .withHttpApp(Router("/" -> Routes.routes(ledger)).orNotFound)
            .build
        )
        .evalTap(s => IO.println(s"miniledger listening on http://${s.address}"))
        .useForever
    yield ()
