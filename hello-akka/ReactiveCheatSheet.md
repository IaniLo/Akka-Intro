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

#### The Error Kernel pattern

Keep important data near the root, delegate the risk to the leaves
- restarts are recursive
- as a result, restarts are more frequent near the leaves
- avoid restarting Actors with important states

#### EventStream

Because Actors can only send messages to a known address, the EventStream allows publication of messages to an unknown audience

```scala
trait EventStream {
  def subscribe(subscriber: ActorRef, topic: Class[_]): Boolean
  def unsubscribe(subscriber: ActorRef, topic: Class[_]): Boolean
  def unsubscribe(subscriber: ActorRef): Unit
  def publish(event: AnyRef): Unit
}


class MyActor extends Actor {
  context.system.eventStream.subscribe(self, classOf[LogEvent])
  def receive = {
    case e: LogEvent => ...
  }
  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(self)
  }
}
```

Unhanlded messages are passed to the Actor's `unhandled(message: Any)` method.

#### Persistent Actor State

The state of an Actor can be stored on disk to prevent data loss in case of a system failure.

There are two ways for persisting state:
- in-place updates mimics what is stored on the disk. This solution allows a fast recovery and limits the space used on the disk.
- persist changes in append-only fashion. This solution allows fast updates on the disk. Because changes are immutable they can be freely be replicated. Finally it allows to analyze the history of a state.
  - Command-Sourcing: persists the messages before processing them, persist acknowledgement when processed. Recovery works by sending the messages to the actor. A persistent Channel discards the messages already sent to the other actors
  - Event-Sourcing: Generate change requests (events) instead of modifying the local state. The events are sent to the log that stores them. The actor can either update its state when sending the event to the log or wait for the log to contact it back (in which case it can buffer any message while waiting for the log).
  - In both cases, immutable snapshots can be made at certain points of time. Recovery only applies recent changes to the latest snapshot.

Each strategy have their upsides and downsides in terms of performance to change the state, recover the state, etc.

The `stash` trait allows to buffer, e.g.

```scala
class MyActor extends Actor with Stash {
  var state: State = ...
  def receive = {
    case NewState(text) if !state.disabled =>
      ... // sends the event to the log
      context.become(waiting, discardOld = false)
  }
  def waiting(): Receive = {
    case e: Event =>
      state = state.updated(e)  // updates the state
      context.unbecome();       // reverts to the previous behavior
      unstashAll()              // processes all the stashed messages
    case _ => stash()           // stashes any message while waiting
  }
}
```

## Clusters

Actors are designed to be distributed. Which means they could be run on different cores, CPUs or even machines.

When actors are distributed across a network, several problems can arise. To begin with, data sharing can only be by value and not by reference. Other networking: low bandwidth, partial failure (some messages never make it)

On a network, Actors have a path that allow to reach them. An `ActorPath` is the full name (e.g. akka.tcp://HelloWorld@198.2.12.10:6565/user/greeter or akka://HelloWorld/user/greeter), whether the actor exists or not, whereas `ActorRef` points to an actor which was started and contains a UID (e.g. akka://HelloWorld/user/greeter#234235234).

You can use `context.actorSelection(path) ! Identify((path, sender))` to convert an ActorPath into an ActorRef. An `ActorIdentity((path: ActorPath, client: ActorRef), ref: Option(ActorRef)` message is then sent back with `ref` being the ActorRef if there is any.

It is also possible to get using `context.actorSelection("...")` which can take a local ("child/grandchild", "../sibling"), full path ("/user/myactor") or wildcards ("/user/controllers/*")

#### Creating clusters

A cluster is a set of nodes where all nodes know about each other and work to collaborate on a common task. A node can join a cluster by either sending a join request to any node of the cluster. It is accepted if all the nodes agree and know about the new node.

The akka-cluster module must be installed and properly configured (akka.actor.provider = akka.cluster.ClusterActorRefProvider). To start a cluster, a node must start a cluster and join it:

```scala
class MyActor extends Actor {
  val cluster = Cluster(context.system)
  cluster.subscribe(self, classOf[ClusterEvent.MemberUp])
  cluster.join(cluster.selfAddress)

  def receive = {
    case ClusterEvent.MemberUp(member) =>
      if (member.address != cluster.selfAddress) {
        // someone joined
      }
  }
}

class MyOtherActor extends Actor {
  val cluster = Cluster(context.system)
  cluster.subscribe(self, classOf[ClusterEvent.MemberUp])
  val main = cluster.selfAddress.copy(port = Some(2552)) // default port
  cluster.join(cluster.selfAddress)

  def receive = {
    case ClusterEvent.MemberRemoved(m, _) => if (m.address == main) context.stop(self)
  }
}
```

It is possible to create a new actor on a remote node

```scala
val node: Address = ...
val props = Props[MyClass].withDeploy(Deploy(scope = RemoteScope(node)))
val controller = context.actorOf(props, "myclass")
```

#### Eventual Consistency

- Strong consistency: after an update completes, all reads will return the updated value
- Weak consistency: after an update, conditions need to be met until reads return the updated value (inconsistency window)
- Eventual Consistency (a form of weak consistency): once no more updates are made to an object there is a time after which all reads return the last written value.

In a cluster, the data is propagated through messages. Which means that collaborating actors can be at most eventually consistent.

#### Actor Composition

Since an Actor is only defined by its accepted message types, its structure may change over time.

Various patterns can be used with actors:

- The Customer Pattern: typical request/reply, where the customer address is included in the original request
- Interceptors: one-way proxy that does not need to keep state (e.g. a log)
- The Ask Pattern: create a temporary one-off ask actor for receiving an email (you can use `import.pattern.ask` and the `?` send message method)
- Result Aggregation: aggregate results from multiple actors
- Risk Delegation: create a subordinate to perform a task that may fail
- Facade: used for translation, validation, rate limitation, access control, etc.

Here is a code sniplet using the ask and aggregation patterns:

```scala
def receive = {
  case Message =>
  val response = for {
    result1 <- (actor1 ? Message1).mapTo[MyClass1]
    result2 <- (actor2 ? Message2).mapTo[MyClass2]  // only called when result1 is received
  } yield ...

  response pipeTo sender
}
```

#### Scalability

Asynchronous messages passing enables vertical scalability (running the computation in parallel in the same node)
Location transparency enables horizontal scalability (running the computation on a cluster of multiple nodes)

Low performance means the system is slow for a single client (high latency)
Low scalability means the system is fast when used by a single client (low latency) but slow when used by many clients (low bandwidth)

With actors, scalability can be achieved by running several stateless replicas concurrently. The incoming messages are dispatched through routing. Routing actor(s) can either be stateful (e.g. round robin, adaptive routing) or stateless (e.g. random, consistent hashing)

- In Adaptive Routing (stateful), routees tell the router about their queue sizes on a regular basis.
- In Consistent Hashing (stateless), the router is splitting incoming messages based on some criterion

Stateful actors can be recovered based on a persisted state, but this means that 1) only one instance must be active at all time and 2) the routing is always to the active instance, buffering messages during recovery.

#### Responsiveness

Responsiveness is the ability to respond within a given time limit. If the goal of resilience is to be available, responsiveness implies resilience to overload scenarios.

Several patterns can be implemented to achieve responsiveness:

1) Exploit parallelism, e.g.

```scala
def receive = {
  case Message =>
    val result1 = (actor1 ? Message1).mapTo[MyClass1]
    val result2 = (actor2 ? Message2).mapTo[MyClass2]  // both calls are run in parallel

    val response = for (r1 <- result1, r2 <- result2) yield { ... }
    response pipeTo sender
}
```

2) Load vs. Responsiveness: When incoming request rate rises, latency typically rises. Hence the need to avoid dependency of processing cost on load and add parallelism elastically, resizing routers.

However, any system has its limits in which case processing gets backlogged.

3) The Circuit Breaker pattern (use akka `CircuitBreaker`) filters the number of requests that can come in when the sytem is under too much load, so that one subsystem being swamped does not affect the other subsystems.

4) With the Bulkheading patterns, one separates computing intensive parts from client-facing parts (e.g. on different nodes), the latter being able to run even if the backend fails.

```scala
Props[MyActor].withDispatcher("compute-jobs")  // tells to run the actor on a different thread

// If not, actors run on the default dispatcher
akka.actor.default-dispatcher {
  executor = "fork-join-executor"
  fork-join-executor {
    parallelism-min = 8
    parallelism-max = 64
    parallelism-factor = 3.0
  }
}

compute-jobs.fork-join-executor {
  parallelism-min = 4
  parallelism-max = 4
}
```

5) Use the Active-Active Configuration. Detecting failures takes time (usually a timeout). When this is not acceptable, instant failover is possible in active-active configurations where 3 nodes process the same request in parallel and send their reponses to the requester. Once the requester receives 2 matching results it considers it has its answer, and will proactively restart a node if it fails to respond within a certain time.

