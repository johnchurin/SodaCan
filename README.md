# SodaCan
This is a proof-of-concept for plant automation using Java 8+, Apache Kafka and JBoss Drools. It is probably overkill for Home Automation but it does provide a lan-based alternative to those systems that depend on a commercial cloud service. Persistence, fault-tolerance, commumication security, and communication is provided by the SodaCan environment. 

If you are looking for something that has all of the fault-tolerance of a cloud-based service (Amazon, Google, etc) in an in-house package, this may eventually be a solution. 

## Device Controllers
Each "device" should be a microcontroller capable of running Java. BeagleBone Black or Wireless BeagleBone Green would be appropriate.

## Rules
The SodaCan contains rules that manages events, device state, alerts, heartbeat, delays, etc. A key concept is that SodaCan rules should be able to reason over all available facts which mostly boils down to all parameters of all devices.

Device parameters are represented as facts in the rule engine. Rules should not change device parameters directly but rather should send a device parameter change request to the device. In response, the device will make the parameter change locally and then send an updated parameter to the SodaCan.

Device Events originate in certain devices, typically buttons or similar triggers. An event doesn't usually persist vert long in the rule engine. For example, a button press used to toggle another device on or off would have little meaning once the target device state change has occured. For this reason, events are stored in a separate Kafka topic from parameters. An event persists in a topic for a configurable amount of time, say one week when it's space is simply reclaimed. Events are not replayed should the SodaCan fail and be restarted.

Device Parameters are handled differently. Logically, the last setting of each unique parameter will remain in the topic. If a SodaCan should fail, when it starts again, it simply rewinds to the beginning of the topic and loads all parameters into working memory. Behind the scenes, the "log compaction process" which retains the most recent parameter state runs only periodically so it is possible that during a load, multiple versions of a single parameter might be processed (in order). This is not a problem for the SodaCan.

## High-level Dataflow
Dataflow from various perspectives and operational conditions are described here.
### Device Controller, steady-state
During normal operation, a device controller maintains a copy of all parameters associated with all devices connected to that microcontroller. This is the "source of truth" for these parameters and no other components should normally change the parameter values. However, when an external component, primary SodaCan, desires to change a parameter, it will send a parameter change message which the microcontroller can use as notification of a request to change a paramter value. In any case, when the microcontroller changers the value of a parameter (or the parameter is added), it must send a message containing the new value.

When appropriate, a microcontroller can send an event rather than changing it's state. The difference between a button-press (event) and say, a temperature reading (a parameter change).

Each microcontroller should also send a "heartbeat" event periordically so that the rules can react to a device being off-line.

### SodaCan, steady-state
SodaCan reacts to parameter changes by inserting a new fact or modifying an existing fact in working memory. Thus, working memory contains, at least, the corrent value of all parameters in the system. Rules will then react as appropriate, or not at all, to parameter changes. Events are processed differently: Most events only spend a brief time in working memory.The tend to "age out" within seconds. Should a rule desire to change a parameter value, such as when a button event causes the state of a light to toggle from on-to-off, the SodaCan does not change it's value but rather sends out a parameter change request, which the device is likely to honor by making the parameter change and sending the updated parameter back to SodaCan.

## Servers
In general, server describes a logical concept. Indeed, a server in this environment may be nothing more than a Docer container.

At least three physical servers should provide sufficient redundancy. While my servers are in a single rack, they could be more distributed to improve reliability. 

The logical organization of servers is: Three Zookeeper servers, three Message Broker servers, and three SodaCan servers. Since all three boxes can be expected to be up most of the time, it is best that each of the three major components runs on each of the boxes. Of course in the case of a failure, the services running on the failed box will shift to processes running on the remaining boxes.

Using the 3x3 configuration described above, one could in theory run these on nine separate boxes. But that would leave 6 boxes idle most of the time. In any case, Docker containers are used to represent each of these nine logical boxes so that they can be deployed over three servers, or on AWS or similar service if desired. Should a phyical box go down, the Docker container can simply be run on a different box.  

## Peristence
Kafka provides all state persistence for SodaCan as well as fast, reliable message delivery. Leader election is provided by Zookeeper which also manages distributed configuration information. There's really no need for a reliable disk configuration due to efficient replication and failover. As a general philosophy, if a server should fail for any reason, it should simply be wiped clean and rebuilt. There should be no need for backups or for any downtime. This philosophy also applies to the disk files used by Kafka: If a server goes down, the kafka files can be wiped. When Kafka restarts, they will be restored from other brokers.

## Cabling
I'm running normal Cat-6 cable to each device controller (BeagleBone). In the case of lighting,  I run traditional RS-485 from a server to any number of DMX-based lighting devices, most of which are custom-made.

## Industrial Standards
The approach used by SodaCan is much less compact than a protocol such as MODBUS. Nevertheless, it is relatively compact and provides built-in security, error detection, failover, etc. This project makes no attempt to comply with the SCADA standard. 

There are products available that support protocol conversion between various standards. That function is beyond the scope of this project.

## Load Balancing
While Kafka provides partitioning which can provide distribution of processing across many systems, scalability (tens-of-thousands of nodes) is not a goal of this project. In Kafka terms, each "topic" has only one partition. A typical system might have hundreds of devices. For a system with tens of tousands of devices, it is likely that rules would not be able to reason over all facts and that a tiered system would be needed. Such could be done with SodaCan as the intermediate tier, but that's not something I'm working on at this point.

## Device Controller Configuration
Some device mirocontrollers can be setup in such a way that, with the exception of its unique host name, is completely configured from a central site using Kafka. To make this efficient, the following approach is used: Each device has its own topic. This topic is only used to send messages from SodaCan to the controller. Anything sent in the other direction (controller to server) is done via the event or state topic. When a controller starts up, it seeks to the beginning of it's "topic" to get configuration information and any state change requests from the SodaCan.
