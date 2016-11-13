# SodaCan
This is a proof-of-concept for plant automation using Java 8+, Apache Kafka and JBoss Drools. It is probably overkill for Home Automation but it does provide a lan-based alternative to those systems that depend on a commercial cloud service. If you are looking for something that has all of the fault-tolerance of a cloud-based service (Amazon, Google, etc) in an in-house package, this may eventually be a solution. 

## Device Controllers
Each "device" should be a microcontroller capable of running Java. BeagleBone Black or Wireless BeagleBone Green would be appropriate.

## Servers
The central servers control device behavior. SodaCan rules use a JBoss Drools rule engine that manages events and device state. Persistence, fault-tolerance, and communication is provided by Apache Kafka. 

At least three physical servers should provide sufficient redundancy. While my servers are in a single rack, they could be more distributed to improve reliability and availability. To provide additional modularity, each of the major components can run in a Docker container. This allows a mix of more logical servers on fewer physical servers and allows replacement of a server (logical or phyisical) with less trouble.  

## Peristence
Kafka provides all state persistence as well as fast, reliable message delivery. Leader election is provided by Zookeeper which also manages distributed configuration. There's really no need for a reliable disk configuration due to efficient replication and failover. As a general philosophy, if a server should fail for any reason, it should simply be wiped clean and rebuilt. There should be no need for backups or for any downtime.

## Cabling
I'm running normal Cat-6 cable to each device controller (BeagleBone). In the case of lighting,  I run traditional RS-485 from a server to any number of DMX-based lighting devices, most of which are custom-made.

