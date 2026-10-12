package miniledger

import io.circe.{Decoder, Encoder}

import java.util.UUID

type AccountId = Int

final case class TransferId(value: UUID)

object TransferId:
  given Encoder[TransferId] = Encoder[String].contramap(_.value.toString)

final case class Transfer(from: AccountId, to: AccountId, amount: BigDecimal)

object Transfer:
  // Plain decimal notation only: no exponents, signs or NaN-likes, so BigDecimal arithmetic stays cheap.
  private val PlainDecimal = """\d+(\.\d+)?""".r

  // Shadows circe's BigDecimal decoder (which also takes JSON numbers) for the derivation below.
  private given Decoder[BigDecimal] = Decoder[String].emap { s =>
    Either
      .cond(PlainDecimal.matches(s), BigDecimal(s), "amount must be a decimal string, e.g. \"10.50\"")
      .filterOrElse(_ > 0, "amount must be positive")
  }

  given Decoder[Transfer] =
    Decoder.derived[Transfer].ensure(t => t.from != t.to, "from and to must differ")

sealed trait Op
final case class Debit(amount: BigDecimal, transfer: TransferId)  extends Op
final case class Credit(amount: BigDecimal, transfer: TransferId) extends Op

/** `log` is newest-first. */
final case class Account(balance: BigDecimal, log: List[Op])

object Account:
  val empty: Account = Account(BigDecimal(0), Nil)

type Accounts = Map[AccountId, Account]

enum Rejection:
  case UnknownSource, InsufficientFunds

enum Status:
  case Pending, Applied
  case Rejected(reason: Rejection)
