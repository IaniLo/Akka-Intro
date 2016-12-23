package com.hello.actors.Bank

import akka.actor.ActorRef
import akka.actor.Actor
import akka.event.LoggingReceive
import akka.actor.actorRef2Scala

object WireTransfer {
  case class Transfer(from: ActorRef, to: ActorRef, amount: BigInt)
  case object Done
  case object Failed
}

class WireTransfer extends Actor {
  import WireTransfer._

  def receive = LoggingReceive {
    case Transfer(from, to, amount) =>
      from ! BankAccount.Withdraw(amount)
      context.become(awaitWithdraw(to, amount, sender))
  }
  def awaitWithdraw(to: ActorRef, amount: BigInt, client: ActorRef): Receive = LoggingReceive {
    case BankAccount.Done =>
      to ! BankAccount.Deposit(amount)
      context.become(awaitDeposit(client))
    case BankAccount.Failed =>
      client ! Failed
      context.stop(self)
  }
  def awaitDeposit(client: ActorRef): Receive = LoggingReceive {
    case BankAccount.Done =>
      client ! Done
      context.stop(self)
  }
}
