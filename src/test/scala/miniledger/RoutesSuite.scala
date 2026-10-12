package miniledger

import cats.effect.IO
import io.circe.Json
import org.http4s.{Status as HttpStatus, *}
import org.http4s.circe.*
import org.http4s.implicits.*
import weaver.SimpleIOSuite

import scala.concurrent.duration.*

object RoutesSuite extends SimpleIOSuite:
  private val cfg = LedgerConfig(queueCapacity = 8, mintingAccounts = Set(0))

  private def post(body: String): Request[IO] =
    Request[IO](Method.POST, uri"/transfer").withEntity(body)

  private def transfer(from: Int, to: Int, amount: String): String =
    s"""{"from":$from,"to":$to,"amount":"$amount"}"""

  private def withApp[A](c: LedgerConfig = cfg, run: Boolean = true)(f: (Ledger, HttpApp[IO]) => IO[A]): IO[A] =
    val ledger = if run then Ledger.start(c) else cats.effect.Resource.eval(Ledger.create(c))
    ledger.use(l => f(l, Routes.routes(l).orNotFound))

  private def awaitStatus(l: Ledger, id: TransferId): IO[Status] =
    l.status(id).flatMap {
      case Some(Status.Pending) | None => IO.sleep(5.millis) >> awaitStatus(l, id)
      case Some(done)                  => IO.pure(done)
    }

  private def transferId(r: Response[IO]): IO[TransferId] =
    r.as[Json].map(j => TransferId(java.util.UUID.fromString(j.hcursor.get[String]("transferId").toOption.get)))

  test("GET /health returns 200") {
    withApp()((_, app) => app.run(Request[IO](Method.GET, uri"/health")).map(r => expect(r.status == HttpStatus.Ok)))
  }

  test("unknown path returns 404") {
    withApp()((_, app) => app.run(Request[IO](Method.GET, uri"/nope")).map(r => expect(r.status == HttpStatus.NotFound)))
  }

  test("POST /transfer accepts a valid transfer and it is eventually applied") {
    withApp() { (l, app) =>
      for
        r      <- app.run(post(transfer(0, 1, "10.50")))
        id     <- transferId(r)
        status <- awaitStatus(l, id)
      yield expect(r.status == HttpStatus.Accepted) and expect(status == Status.Applied)
    }
  }

  test("POST /transfer from an unknown account is eventually rejected") {
    withApp() { (l, app) =>
      for
        r      <- app.run(post(transfer(7, 1, "1")))
        id     <- transferId(r)
        status <- awaitStatus(l, id)
      yield expect(r.status == HttpStatus.Accepted) and
        expect(status == Status.Rejected(Rejection.UnknownSource))
    }
  }

  List(
    "from == to"       -> transfer(1, 1, "5"),
    "zero amount"      -> transfer(0, 1, "0"),
    "negative amount"  -> transfer(0, 1, "-5"),
    "exponent amount"  -> transfer(0, 1, "1e9"),
    "non-numeric"      -> transfer(0, 1, "abc"),
    "numeric amount"   -> """{"from":0,"to":1,"amount":5}""",
    "missing field"    -> """{"from":0,"amount":"5"}""",
    "malformed json"   -> "{nope"
  ).foreach { (name, body) =>
    test(s"POST /transfer rejects $name with 400") {
      withApp() { (_, app) =>
        for
          r <- app.run(post(body))
          j <- r.as[Json]
        yield expect(r.status == HttpStatus.BadRequest) and
          expect(j.hcursor.get[String]("error").isRight)
      }
    }
  }

  test("POST /transfer returns 503 when the queue is full") {
    withApp(cfg.copy(queueCapacity = 1), run = false) { (_, app) =>
      for
        a <- app.run(post(transfer(0, 1, "1")))
        b <- app.run(post(transfer(0, 1, "1")))
      yield expect(a.status == HttpStatus.Accepted) and expect(b.status == HttpStatus.ServiceUnavailable)
    }
  }
