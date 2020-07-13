package com.hdfc.txnalerts.failedaxiom.utils;

import java.io.BufferedReader;
import java.io.FileReader;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.log4j.Logger;

public class Utils implements Constants{

	private static Logger logger = Logger.getLogger(Utils.class);
	
	public static String readFile(String filePath) throws Exception {
	    String result = "";
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();
        while (line != null) {
            sb.append(line);
            line = br.readLine();
        }
        result = sb.toString();
        br.close();
	    return result;
	}
	
	public static void sendRecordToTopic(ProducerRecord<String, String> record, KafkaProducer<String, String> producer) {
		try {
			RecordMetadata metadata = producer.send(record).get();
			logger.info(String.format("Record sent (topic = %s, offset = %s)", metadata.topic(), metadata.offset()));
		} catch (Exception e) {
			logger.error(String.format("Error occurred in producing message to topic %s", record.topic()), e);
		}
	} 
	
	public static boolean isStringNotNullAndNotEmpty(String str) {
		return ! isStringNullOrEmpty(str);
	}

	public static boolean isStringNullOrEmpty(String str) {
		return (str == null || str.isEmpty());
	}
	
	private static String getMapKey(TopicPartition partition) {
		return String.format("partition-%d", partition.partition());
	}
	
}
