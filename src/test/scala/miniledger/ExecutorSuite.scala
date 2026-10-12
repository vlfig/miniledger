package miniledger

import cats.Show
import org.scalacheck.Gen
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

import java.util.UUID

object ExecutorSuite extends SimpleIOSuite with Checkers:
  private val minting = Set(0)
  private val start: Accounts = Map(0 -> Account.empty)
  private def id() = TransferId(UUID.randomUUID())

  private def run(s: Accounts, t: Transfer) =
    val (log, next, status) = Executor.execute(id(), t).run(minting, s).value
    (log.toList, next, status)

  pureTest("minting account can go negative and creates the destination") {
    val (log, s, st) = run(start, Transfer(0, 1, 10))
    expect(log.isEmpty) and expect(st == Status.Applied) and expect(s(0).balance == -10) and expect(s(1).balance == 10)
  }

  pureTest("unknown source is rejected and leaves state untouched") {
    val (log, s, st) = run(start, Transfer(5, 1, 10))
    expect(st == Status.Rejected(Rejection.UnknownSource)) and expect(log == List(Rejection.UnknownSource)) and expect(s == start)
  }

  pureTest("insufficient funds is rejected and creates no destination") {
    val (_, s1, _) = run(start, Transfer(0, 1, 10))
    val (log, s2, st) = run(s1, Transfer(1, 2, 11))
    expect(st == Status.Rejected(Rejection.InsufficientFunds)) and expect(log == List(Rejection.InsufficientFunds)) and expect(s2 == s1)
  }

  pureTest("balances sum to zero and equal the fold of each log") {
    val transfers = List(Transfer(0, 1, 10), Transfer(1, 2, BigDecimal("3.5")), Transfer(2, 1, 1), Transfer(1, 9, 99))
    val end = transfers.foldLeft(start)((s, t) => run(s, t)._2)
    def fold(a: Account) = a.log.map { case Debit(x, _) => -x; case Credit(x, _) => x }.sum
    expect(end.values.map(_.balance).sum == 0) and
      expect(end.values.forall(a => fold(a) == a.balance)) and
      expect(end.filter((k, _) => k != 0).values.forall(_.balance >= 0))
  }

  private val ids = Gen.choose(0, 5)

  private val genAccounts: Gen[Accounts] =
    val account = ids.flatMap { k =>
      // minting accounts hold money owed, everyone else holds money
      val balance = if minting(k) then Gen.choose(-1000, 0) else Gen.choose(0, 1000)
      balance.map(b => k -> Account(BigDecimal(b), Nil))
    }
    Gen.listOf(account).map(_.toMap)

  private val genTransfer: Gen[Transfer] =
    for
      from   <- ids
      offset <- Gen.choose(1, 5)
      amount <- Gen.choose(1, 1000)
    yield Transfer(from, (from + offset) % 6, BigDecimal(amount))

  private given Show[(Accounts, Transfer)] = Show.fromToString

  test("a rejected transfer leaves the state unchanged") {
    forall(Gen.zip(genAccounts, genTransfer)) { (s, t) =>
      val (_, next, status) = Executor.execute(id(), t).run(minting, s).value
      expect(status match
        case Status.Rejected(_) => next == s
        case _                  => true
      )
    }
  }
