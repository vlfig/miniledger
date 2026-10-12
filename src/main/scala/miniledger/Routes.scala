package miniledger

import cats.effect.IO
import io.circe.{Encoder, Json}
import io.circe.syntax.*
import org.http4s.{DecodeFailure, EntityDecoder, HttpRoutes, InvalidMessageBodyFailure}
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.circe.jsonOf
import org.http4s.dsl.io.*

object Routes:
  private given EntityDecoder[IO, Transfer] = jsonOf[IO, Transfer]

  private final case class ErrorBody(error: String) derives Encoder.AsObject
  private final case class AcceptedBody(transferId: TransferId) derives Encoder.AsObject

  private def reason(f: DecodeFailure): String = f match
    case InvalidMessageBodyFailure(_, Some(cause)) => cause.getMessage
    case other                                     => other.message

  def routes(ledger: Ledger): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health" => Ok(Json.obj("status" -> Json.fromString("ok")))
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
