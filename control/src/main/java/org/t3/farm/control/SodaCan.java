package org.t3.farm.control;

import org.t3.farm.control.DevParam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.t3.farm.control.Engine;
import org.t3.farm.control.ParameterChangeSender;

public class SodaCan implements ConsumerRebalanceListener {
	private Engine engine = null;
	private KafkaConsumer<String, String> consumer = null;
	static Logger logger = LoggerFactory.getLogger(SodaCan.class);
	List<String> topics = new ArrayList<String>();
	List<String> rewindTopics = new ArrayList<String>();

	public SodaCan( ) {
		topics.add("e");
		topics.add("dp");
		rewindTopics.add("dp");
	}
	
	public void connect() {
		Properties props = new Properties();
		props.put("bootstrap.servers", "shop1:9092");
		props.put("group.id", "SodaGroup");
		props.put("enable.auto.commit", "true");		// Offset is saved automatically
		props.put("auto.commit.interval.ms", 10*1000);	// at this interval
		props.put("auto.offset.reset", "earliest");
		props.put("session.timeout.ms", "30000");
		props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		// Setup a consumer
		consumer = new KafkaConsumer<String, String>(props);
		// We don't actually get started until we've been assigned our topics/partitions
		consumer.subscribe(topics, this);
	}
	
	@Override
	public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
		if (!partitions.isEmpty()) {
			logger.debug("Topic assignment(s) revoked: " + partitions + " stopping rules");
			if (engine!=null) {
				engine.close();
				engine = null;
			}
		}
	}
	/**
	 * Once we are assigned partitions, then we can start up the rule engine
	 * @param partitions
	 */
	@Override
	public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
		// When we are assigned a DP topic, we must start at the beginning of the topic
		if (!partitions.isEmpty()) {
			logger.debug("Assigned to topic(s): " + partitions );
			partitions.forEach((tp)->{ if (rewindTopics.contains(tp.topic())) {
				consumer.seek(tp, 0);
				logger.debug("Rewinding " + tp);
				}});
		}
	}

	public void run() {
//		long startTime = System.currentTimeMillis();
		boolean go = true;
		while (go) {
//					logger.info("Polling");
			ConsumerRecords<String, String> records = consumer.poll(1000);
			if (!records.isEmpty() && engine==null) {
				engine = new Engine();
				engine.addEventListener(new ParameterChangeSender());
			}
//					logger.info("Process any messages we got");
			for (ConsumerRecord<String, String> record : records) {
				// Device Parameter received, update working memory
				if ("dp".equals(record.topic())) {
					DevParam dp = new DevParam(record.key(), record.value());
					engine.insertDevParam(dp);
				} else if ("e".equals(record.topic())) {
					if ("button".equals(record.value())) {
						engine.insertDevEvent(new ButtonEvent(record.key()));
//						System.out.println("Heartbeat count: " + engine.getHearbeatCount());
					} else if ("heartbeat".equals(record.value())) {
						engine.insertDevEvent (new HeartbeatEvent(record.key()));
					} else {
						logger.warn("invalid event type: " + record.value());
					}
				}
			}
		}
		
	}
	public static void main(String[] args) {
		SodaCan sc = new SodaCan();
		sc.connect();
		sc.run();
	}
	
}
