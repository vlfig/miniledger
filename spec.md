# Teya assignment

You are expected to implement a set of apis to power a simple ledger.
From a functional perspective, the following features should be implemented:
• Ability to record money movements (ie: deposits and withdrawals)
• View current balance
• View transaction history
From a technical perspective:
• We expect you to deliver a functional web application (no UI, just the apis)
that can be run locally.
• You can use any programming language and framework of your choice.
• For the sake of simplicity, we strongly suggest you use in-memory data
structures to store the data (for example: a map or an array), and it should not
be necessary to install any optional software to run it (besides any libraries
that you choose to use).
You are free to make assumptions whenever you feel it is necessary, but please
document them.
Please try to keep it simple.

---

# Implementation, the prototype way.

## Domain

```
type AccountId = Int
type TransferId = UUID // generated on accept
case class Transfer(from: AccountId, to: AccountId, amount: BigDecimal)
sealed trait Op
case class Debit(amount: BigDecimal, transfer: TransferId) extends Op
case class Credit(amount: BigDecimal, transfer: TransferId) extends Op
case class Account(balance: BigDecimal, log: List[Op]) // log is newest-first

enum Status:
    case Pending, Applied, Rejected(reason: Rejection)
enum Rejection:
    case UnknownSource, InsufficientFunds

sealed trait ApiError
case class AccountDoesNotExist extends ApiError
```

A transfer is invalid if `from == to` or `amount` is not positive.
`amount` is sent as a plain decimal string (`"10.50"`; no exponent or sign); JSON numbers are rejected.

There is an `f` such that for every account `a`, `f(a.log) == a.balance`.

There's a transaction queue that is produced by the API thread and has a consumer on a different thread takes each of them and executes them in order.

## API

One API, four endpoints:

- POST /transfer
- GET /`<account>`/balance
- GET /`<account>`/history
- GET /status/`<transferId>`

The brief does not mention account creation. We could be lazy about it and assume an account gets created whenever there
is a movement towards it, but where does the money come from? We could have a set of "minting" accounts given as configuration whose balance can go negative. These can always transfer, and thus through the same lazy mechanism create other accounts.

Consistency between POST and GETs is eventual, but strict among GETs. Most importantly transfers are serializable.

Status of a transaction is made eventual as to separate the execution from the accepting (and ordering) of a transaction. Otherwise, write throughput would be limited by the thread pool of inserts waiting on the executor.

### POST /transfer

Request

`Transfer`

Response

`Either[StatusCode.Accepted(transferId), StatusCode.BadRequest | StatusCode.ServiceUnavailable]`

Bodies: `{"transferId": "<uuid>"}` or `{"error": "..."}`. 400 for invalid transfers and malformed JSON; 503 when the queue is full.

### GET /`<account>`/balance

Response

`Either[Balance, AccountDoesNotExist]`

Bodies: `{"balance": "10.50"}` (decimal string, as in POST) or `{"error": "..."}`. 404 for an unknown account, 400 for a non-integer account id. Minting accounts are visible (negative balances).

Races with the executor, but is eventually correct.

### GET /`<account>`/history

Response

`Either[Vector[Op], AccountDoesNotExist]` (order is newest-first)

Races with the executor, but is eventually correct.

## Implementation overview

### General notes

all data stores are in-memory
this is a prototype, keeping things simple
not worrying about pagination, 

### Init

Initialise a singleton bounded FIFO transaction queue (`queue-capacity`, configured).
Read configuration; the set of minting accounts is the executor's Reader `R`, and each is seeded in the state with balance 0.
Initialise StatusStore.
Initialise executor.
Initialise API, start listening.

### Transfer endpoint

Deserialize, check validity, check queue not full, and put it in the queue.

### Executor

Listens on the other end of the queue. Runs a pure ReaderWriterStateT where `S` the state is all the accounts, `L` the written log contains any errors with the transaction, `A` is the result, the transaction `Status`, to be fed to the StatusStore. No other state is used.

`S` must publish atomically, i.e. an API reader thread will always read mutually consistent state.

Minting accounts skip the balance check. The destination is created only when the transfer applies.

Rejections leave state unchanged.

Ordering: `Pending` is set before enqueueing (and removed if the queue is full); state is published before the status is set.

Rejects the transaction:
- if the source account is unknown / does not exist;
- if the source account does not have enough balance.

#### invariants

- all normal (non-minting) accounts must have a non-negative balance
- all the balances add up to zero after the execution of each transaction

## Assumptions

- No authentication: anyone can transfer from any account, including minting ones.
- No idempotency: a retried POST is a new transfer.
- Nothing is durable; a restart wipes everything.
- An unexpected executor exception is not handled.
