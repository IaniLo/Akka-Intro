

In this tutorial you'll find three projects:

1. The first one called Counter is the simplest akka program that exchanges messages between two Actors. But anyone can play with this one to discover the world of akka. Like passing a mutable list with some values and generating a number of actors and adding its name to the head of the list and observed the behavior.

2. The second one is an evolution from the first one solving the BankAccount problem to to facilitate a transaction between two actors trying to withdraw/deposit at the same time. This one introduces a third Actor who will play the role of transaction manager to facilitate those transfers.(This actor is called TransferMoney) 

3. The third one more complicated is based on the Receptionist sample. This one covers all phases from design to test!

In this example you are dealing with a Receptionist that is receiving a request from a Client. The request is un url, the client wanted to be parsed, found all links under a certain depth and expect back a list of links.
The Receptionist has two states "waiting" state or "running" state. Once it retrieves a request he creates another actor called "Controller" and passes the url along with the depth of the search on each page.
The Controller will have to among other things has to keep track of all the links visited (otherwise we may go in a loop). He will create another actor called "Getter" to whom he passes the url (Get (url)) and expects back a list of links, if everything goes well.
A Getter actor will parse a page and at the end send each link found one by one to the Controller, which in turn will spawn another Getter for each url.




![](Receptionist.png?raw=true "Optional Title")


For testing a simple solution is to add overridable methods. I created a full test package where I rewrote some of the actors in order to see the changes to adopt the code to factory methods.   



