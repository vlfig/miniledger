## Intro / first thoughts

I'll be registering my thoughts here, as well as my assumptions and things. This will become the spec (of sorts).

So, we need to build a tiny ledger.

A ledger is an ordered sequence of transactions. The Venetians invented double-entry ledgers mostly for the benefit of
having more than one party keeping them and with that require more than one party to misbehave in order to inscribe a
wrongful transaction. But digital ledgers don't need *double entry* as in a second copy, per se. They need only more
than one party signing a transaction, and for the transactions to have at least a causal ordering.

However, ordering itself can be used to the advantage of some peers over others. That's why distributed systems go to
great lengths to ensure that no one party can control it.

For this assignment we necessarily care about ordering of transactions (subtraction is non-associative), but we'll be
making two important assumptions:

- we don't need to be distributed about it; nor
- be smart about partitioning as to approximate causal ordering and parallelise transactions that do not interact.

If these hold, and the ordering is local to one single active replica (others on standby strictly for reliability) we
should achieve a throughput "finger-in-the-air" of about 50-100 tps with durability (kafka/redpanda benchmarks).
Single-partition, to ensure total ordering.

So, the first step is to receive transactions and order them. Next, we need to both execute them and make them durable.
If we fail here it's just a missed transaction, and the user can try again. Ordering isn't compromised. There's probably
room for speculative execution in parallel with persisting them but that's another avenue with high complexity cost.

So the third step is executing, which can be seen as changing the state of the system in the way intended by the
transaction, authorised by the parties, and ensuring the invariants of the system.

The prototypical transaction (and the only type we care about now) is that of moving a quantity from one account to
another. Therefore, at least two data have to change: the accounts. One has to be credited and the other debited.

Give the requirement to check the history of an account, it seems a good design to have accounts be event-sourced too.
Debits and credits are appended as transactions get executed. History is kept according to chosen parameters, and the
balance is just a pure function folding over that sequence of events (only depending on the offset, assuming the log is
append-only) which can be memoised and kept up-to-date as events happen. This also plays well with the compaction of
topics in kafka/redpanda.
