package miniledger

import cats.effect.{IO, Ref, Resource}
import cats.effect.std.Queue

final case class Queued(id: TransferId, transfer: Transfer)

final class StatusStore private (ref: Ref[IO, Map[TransferId, Status]]):
  def set(id: TransferId, s: Status): IO[Unit]   = ref.update(_.updated(id, s))
  def remove(id: TransferId): IO[Unit]           = ref.update(_ - id)
  def get(id: TransferId): IO[Option[Status]]    = ref.get.map(_.get(id))

object StatusStore:
  def create: IO[StatusStore] = Ref.of[IO, Map[TransferId, Status]](Map.empty).map(new StatusStore(_))

/** Accepts transfers into an ordered, bounded queue; a single consumer applies them one at a time. */
final class Ledger private (
    minting: Set[AccountId],
    queue: Queue[IO, Queued],
    state: Ref[IO, Accounts], // atomic change
    statuses: StatusStore
):
  /** `None` if the queue is full. */
  def submit(t: Transfer): IO[Option[TransferId]] =
    for
      id <- IO.randomUUID.map(TransferId(_))
      // Pending goes in before the offer, or the executor could finish first and be overwritten.
      _  <- statuses.set(id, Status.Pending)
      ok <- queue.tryOffer(Queued(id, t))
      _  <- IO.unlessA(ok)(statuses.remove(id))
    yield Option.when(ok)(id)

  def balance(account: AccountId): IO[Option[BigDecimal]] = state.get.map(_.get(account).map(_.balance))

  def status(id: TransferId): IO[Option[Status]] = statuses.get(id)

  private def process(q: Queued): IO[Unit] =
    for
      accounts <- state.get
      (_, next, status) = Executor.execute(q.id, q.transfer).run(minting, accounts).value
      _ <- state.set(next)
      _ <- statuses.set(q.id, status)
    yield ()

  private val consume: IO[Nothing] = queue.take.flatMap(process).foreverM

object Ledger:
  /** A ledger with no consumer running. */
  def create(cfg: LedgerConfig): IO[Ledger] =
    for
      queue    <- Queue.bounded[IO, Queued](cfg.queueCapacity)
      state    <- Ref.of[IO, Accounts](cfg.mintingAccounts.map(_ -> Account.empty).toMap)
      statuses <- StatusStore.create
    yield new Ledger(cfg.mintingAccounts, queue, state, statuses)

  /** A ledger whose executor runs in the background for the lifetime of the resource. */
  def start(cfg: LedgerConfig): Resource[IO, Ledger] =
    Resource.eval(create(cfg)).flatMap(l => l.consume.background.map(_ => l))
