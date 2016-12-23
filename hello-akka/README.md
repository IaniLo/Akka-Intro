

In this tutorial you'll find three projects:

1. The first one called Counter is the simplest akka program that exchanges messages between two Actors. But anyone can play with this one to discover the world of akka. Like passing a mutable list with some values and generating a number of actors and adding its name to the head of the list and observed the behavior.

2. The second one is an evolution from the first one solving the BankAccount problem to to facilitate a transaction between two actors trying to withdraw/deposit at the same time. This one introduces a third Actor who will play the role of transaction manager to facilitate those transfers.(This actor is called TransferMoney) 

3. The third one more complicated is based on the Receptionist sample. This one covers all phases from design to test!







