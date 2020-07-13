package com.hdfc.txnalerts.failedaxiom.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.Logger;

public class KafkaConfig {

	private static Properties producerProps = new Properties();
	private static String mDCProcessedProducerTopic;
	private static String mCCProcessedProducerTopic;
	private static String failureProducerTopic;
	
	private static Logger logger = Logger.getLogger(KafkaConfig.class);
	
	public static void loadConfig() throws Exception {
		
		String kafkaConfigFile = AxiomRetryConfig.getKafkaConfigFile();
		Properties fileProps = new Properties();
		try (InputStream input = new FileInputStream(kafkaConfigFile)) {
			fileProps.load(input);
		} catch (IOException e) {
			logger.error(String.format("Exception occurred while reading file at Location: %s", kafkaConfigFile), e);
			throw e; 
		}
		
		mDCProcessedProducerTopic = fileProps.getProperty("cc.processed.producer.topic");
		mCCProcessedProducerTopic = fileProps.getProperty("dc.processed.producer.topic");
		
		failureProducerTopic = fileProps.getProperty("producer.failure.topic");
		producerProps.load(new FileInputStream(fileProps.getProperty("producer.prop").trim()));
	}

	public static Properties getProducerProps() {
		return producerProps;
	}
	
	public static String getDCProcessedProducerTopic() {
		return mDCProcessedProducerTopic;
	}

	public static String getCCProcessedProducerTopic() {
		return mCCProcessedProducerTopic;
	}

	public static String getDBFailureProducerTopic() {
		return failureProducerTopic;
	}

}
