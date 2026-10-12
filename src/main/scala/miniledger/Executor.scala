package miniledger

import cats.data.{Chain, ReaderWriterState}

/** The pure transfer semantics. `R` is the set of minting accounts, `L` any rejections, `S` all accounts, `A` the outcome. */
object Executor:
  type Exec[A] = ReaderWriterState[Set[AccountId], Chain[Rejection], Accounts, A]

  def execute(id: TransferId, t: Transfer): Exec[Status] =
    ReaderWriterState { (minting, accounts) =>
      accounts.get(t.from) match
        case None =>
          (Chain.one(Rejection.UnknownSource), accounts, Status.Rejected(Rejection.UnknownSource))
        case Some(src) if !minting(t.from) && src.balance < t.amount =>
          (Chain.one(Rejection.InsufficientFunds), accounts, Status.Rejected(Rejection.InsufficientFunds))
        case Some(src) =>
          val debited = accounts.updated(
            t.from,
            Account(src.balance - t.amount, Debit(t.amount, id) :: src.log)
          )
          val dst = debited.getOrElse(t.to, Account.empty)
          val credited = debited.updated(
            t.to,
            Account(dst.balance + t.amount, Credit(t.amount, id) :: dst.log)
          )
          (Chain.empty, credited, Status.Applied)
    }
