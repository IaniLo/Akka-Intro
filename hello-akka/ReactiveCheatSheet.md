---
layout: page
title: Akka Actor Cheat Sheet
---


## Actors

Actors represent objects and their interactions, resembling human organizations. They are useful to deal with the complexity of writing multi-threaded applications (with their synchronizations, deadlocks, etc.)

```scala
type Receive = PartialFunction[Any, Unit]

trait Actor {
  def receive: Receive
}
```

An actor has the following properties:
- It is an object with an identity
- It has a behavior
- It only interacts using asynchronous message

Note: to use Actors in Eclipse you need to run a Run Configuration whose main class is `akka.Main` and whose Program argument is the full Main class name

An actor can be used as follows:

```scala
import akka.actor._

class Counter extends Actor {
  var count = 0
  def receive = {
    case "incr" => count += 1
    case ("get", customer: ActorRef) => customer ! count // '!' means sends the message 'count' to the customer
    case "get" => sender ! count // same as above, except sender means the sender of the message
  }
}
```

#### The Actor's Context

The Actor type describes the behavior (represented by a Receive, which is a PartialFunction), the execution is done by its ActorContext. An Actor can change its behavior by either pushing a new behavior on top of a stack or just purely replace the old behavior.

```scala
trait ActorContext {
  def become(behavior: Receive, discardOld: Boolean = true): Unit // changes the behavior
  def unbecome(): Unit                                            // reverts to the previous behavior
  def actorOf(p: Props, name: String): ActorRef                   // creates a new actor
  def stop(a: ActorRef): Unit                                     // stops an actor
  def watch(target: ActorRed): ActorRef                           // watches whenever an Actor is stopped
  def unwatch(target: ActorRed): ActorRef                         // unwatches
  def parent: ActorRef                                            // the Actor's parent
  def child(name: String): Option[ActorRef]                       // returns a child if it exists
  def children: Iterable[ActorRef]                                // returns all supervised children
}

class myActor extends Actor {
   ...
   context.parent ! aMessage // sends a message to the parent Actor
   context.stop(self)        // stops oneself
   ...
}
```


The following example is changing the Actor's behavior any time the amount is changed. The upside of this method is that 1) the state change is explicit and done by calling `context.become()` and 2) the state is scoped to the current behavior.

```scala
class Counter extends Actor {
  def counter(n: Int): Receive = {
    case "incr" => context.become(counter(n + 1))
    case "get" => sender ! n
  }
  def receive = counter(0)
}
```

#### Children and hierarchy

Each Actor can create children actors, creating a hierarchy.

```scala
class Main extends Actor {
  val counter = context.actorOf(Props[Counter], "counter")  // creates a Counter actor named "counter"

  counter ! "incr"
  counter ! "incr"
  counter ! "incr"
  counter ! "get"

  def receive = {	// receives the messages from Counter
    case count: Int =>
      println(s"count was $count")
      context.stop(self)
    }
  }
}
```

Each actor maintains a list of the actors it created:
- the child is added to the list when context.actorOf returns
- the child is removed when Terminated is received
- an actor name is available IF there is no such child. Actors are identified by their names, so they must be unique.

#### Message Processing Semantics

There is no direct access to an actor behavior. Only messages can be sent to known adresses (`ActorRef`). Those adresses can be either be oneself (`self`), the address returned when creating a new actor, or when received by a message (e.g. `sender`)

Actors are completely insulated from each other except for messages they send each other. Their computation can be run concurrently. However, a specific actor is single-threaded - its messages are received sequentially. Processing a message is the atomic unit of execution and cannot be interrupted.

It is good practice to define an Actor's messages in its companion object. Here, each operation is effectively synchronized as all messages are serialized.

```scala
object BankAccount {
  case class Deposit(amount: BigInt) {
    require(amount > 0)
  }
  case class Withdraw(amount: BigInt) {
    require(amount > 0)
  }
  case object Done
  case object Failed
}

class BankAccount extends Actor {
  import BankAccount._

  var balance = BigInt(0)

  def receive = {
    case Deposit(amount) => balance += amount
                            sender ! Done
    case Withdraw(amount) if amount <= balance => balance -= amount
                                                  sender ! Done
    case _ => sender ! Failed
  }
}
```

Note that `pipeTo` can be used to foward a message (`theAccount deposit(500) pipeTo sender`)

Because communication is through messages, there is no delivery guarantee. Hence the need of messages of acknowledgement and/or repeat. There are various strategies to deal with this:
- at-most-once: send a message, without guarantee it will be received
- at-least-once: keep sending messages until an ack is received
- exactly-once: keep sending messages until an ack is received, but the recipient will only process the first message

You can call `context.setReceiveTimeout(10.seconds)` that sets a timeout:

```scala
def receive = {
  case Done => ...
  case ReceiveTimeout => ...
}
```

The Akka library also includes a scheduler that sends a message or executes a block of code after a certain delay:

```scala
trait Scheduler {
  def scheduleOnce(delay: FiniteDuration, target: ActorRef, msg: Any)
  def scheduleOnce(delay: FiniteDuration)(block: => Unit)
}
```

#### Designing Actor Systems

When designing an Actor system, it is useful to:
- visualize a room full of people (i.e. the Actors)
- consider the goal to achieve
- split the goal into subtasks that can be assigned to the various actors
- who needs to talk to whom?
- remember that you can easily create new Actors, even short-lived ones
- watch out for any blocking part
- prefer immutable data structures that can safely be shared
- do not refer to actor state from code running asynchronously

Consider a Web bot that recursively download content (down to a certain depth):
- one Client Actor, which is sending download requests
- one Receptionist Actor, responsible for accepting incoming download requests from Clients. The Receptionist forwards the request to the Controller
- one Controller Actor, noting the pages already downloaded and dispatching the download jobs to Getter actors
- one or more Getter Actors whose job is to download a URL, check its links and tell the Controller about those links
- each message between the Controller and the Getter contains the depth level
- once this is done, the Controller notifies the Receptionist, who remembers the Client who asked for that request and notifies it

#### Testing Actor Systems

Tests can only verify externally observable effects. Akka's TestProbe allows to check that:

```scala
implicit val system = ActorSystem("TestSys")
val myActor = system.actorOf(Props[MyActor])
val p = TestProbe()
p.send(myActor, "Message")
p.exceptMsg("Ack")
p.send(myActor, "Message")
p.expectNoMsg(1.second)
system.shutdown()
```

It can also be run from inside TestProbe:

```scala
new TestKit(ActorSystem("TestSys")) with ImplicitSender {
  val myActor = system.actorOf(Props[MyActor])
  myActor ! "Message"
  expectMsg("Ack")
  send(myActor, "Message")
  expectNoMsg(1.second)
  system.shutdown()
}
```

You can use dependency injection when the system relies from external sources, like overriding factory methods that work as follows:
- have a method that will call `Props[MyActor]`
- its result is called by context.actorOf()
- the test can define a "fake Actor" (`object FakeMyActor extends MyActor { ... }`) that will override the method

You should start first the "leaves" actors and work your way to the parent actors.

#### Logging Actor Systems

You can mix in your actor with `ActorLogging`, and use various log methods such as `log.debug` or `log.info`.

To see all the messages the actor is receiving, you can also define `receive` method as a `LoogingReceive`.

```scala
def receive: Receive = LoggingReceive {
  case Replicate =>
  case Snapshot =>
}
```

To see the log messages turn on akka debug level by adding the following in your run configuration.

    -Dakka.loglevel=DEBUG -Dakka.actor.debug.receive=on

Alternatively, create a file named `src/test/resources/application.conf` with the following content:

    akka {
      loglevel = "DEBUG"
      actor {
        debug {
          receive = on
        }
      }
    }

#### Failure handling with Actors

What happens when an error happens with an actor? Where shall failures go? With the Actor models, Actors work together in teams (systems) and individual failures are handled by the team leader.

Resilience demands containment (i.e. the failure is isolated so that it cannot spread to other components) and delegation of failure (i.e. it is handled by someone else and not the failed component)

In the Supervisor model, the Supervisor needs to create its subordinates and will handle the exceptions encountered by its children. If a child fails, the supervisor may decide to stop it (`stop` message) or to restart it to get it back to a known good state and initial behavior (in Akka, the ActorRef stays valid after a restart).

An actor can decide a strategy by overriding `supervisorStrategy`, e.g.

```scala
class myActor extends Actor {
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 5) {
     case _: Exception => SupervisorStrategy.Restart
  }
}
```

#### Lifecycle of an Actor

- An Actor will have its context create a child Actor, and gets `preStart()` called.
- In case of a failure, the supervisor gets consulted. The supervisor can stop the child or restart it (a restart is not externally visible). In case of a restart, the child Actor's `preRestart()` gets called. A new instance of the actor is created, after which its `postRestart()` method gets called. No message gets processed between the failure and the restart.
- An actor can be restarted several times.
- An actor can finally be stopped. It sends Stop to the context and its `postStop()` method will be called.

An Actor has the following methods that can be overridden:

```scala
trait Actor {
  def preStart(): Unit
  def preRestart(reason: Throwable, message: Option[Any]): Unit // the default behavior is to stop all children
  def postRestart(reason: Throwable): Unit                      // the default behavior is to call preStart()
  def postStop(): Unit
}
```

#### Lifecycle Monitoring

To remove the ambiguity where a message doesn't get a response because the recipient stopped or because the network is down, Akka supports Lifecycle Monitoring, aka DeathWatch:
- an Actor registers its interest using `context.watch(target)`
- it will receive a `Terminated(target)` message when the target stops
- it will not receive any direct messages from the target thereafter

The watcher receives a `Terminated(actor: ActorRef)` message:
- It is a special message that our code cannot send
- It comes with two implicit boolean flags: `existenceConfirmed` (was the watch sent when the target was still existing?) and `addressTerminated` (the watched actor was detected as unreachable)
- Terminated messages are handled by the actor context, so cannot be forwarded




