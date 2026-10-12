package miniledger

import cats.effect.IO
import cats.syntax.all.*
import io.circe.{Encoder, Json}
import io.circe.syntax.*
import org.http4s.{DecodeFailure, EntityDecoder, HttpRoutes, InvalidMessageBodyFailure}
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.circe.jsonOf
import org.http4s.dsl.io.*

import java.util.UUID
import scala.util.Try

object Routes:
  private given EntityDecoder[IO, Transfer] = jsonOf[IO, Transfer]

  private final case class ErrorBody(error: String) derives Encoder.AsObject
  private final case class BalanceBody(balance: String) derives Encoder.AsObject
  private final case class AcceptedBody(transferId: TransferId) derives Encoder.AsObject

  private def reason(f: DecodeFailure): String = f match
    case InvalidMessageBodyFailure(_, Some(cause)) => cause.getMessage
    case other                                     => other.message

  def routes(ledger: Ledger): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health" => Ok(Json.obj("status" -> Json.fromString("ok")))

    case GET -> Root / "tx" / id / "status" =>
      Try(TransferId(UUID.fromString(id))).toOption.flatTraverse(ledger.status).flatMap {
        case Some(s) => Ok(s.asJson)
        case None    => NotFound(ErrorBody("transfer does not exist").asJson)
      }
    case GET -> Root / account / "balance" =>
      account.toIntOption match
        case None => BadRequest(ErrorBody("account id must be an integer").asJson)
        case Some(id) =>
          ledger.balance(id).flatMap {
            case Some(b) => Ok(BalanceBody(b.bigDecimal.toPlainString).asJson)
            case None    => NotFound(ErrorBody("account does not exist").asJson)
          }
    case GET -> Root / account / "history" =>
      account.toIntOption match
        case None => BadRequest(ErrorBody("account id must be an integer").asJson)
        case Some(id) =>
          ledger.history(id).flatMap {
            case Some(ops) => Ok(ops.asJson)
            case None      => NotFound(ErrorBody("account does not exist").asJson)
          }
    case req @ POST -> Root / "transfer" =>
      req.attemptAs[Transfer].foldF(
        f => BadRequest(ErrorBody(reason(f)).asJson),
        t =>
          ledger.submit(t).flatMap {
            case Some(id) => Accepted(AcceptedBody(id).asJson)
            case None     => ServiceUnavailable(ErrorBody("queue full").asJson)
          }
      )
  }
