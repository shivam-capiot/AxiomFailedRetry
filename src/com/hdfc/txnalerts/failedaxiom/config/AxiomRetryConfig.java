package com.hdfc.txnalerts.failedaxiom.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

public class AxiomRetryConfig {

	private static String kafkaConfigFile;
	private static String dBConfigFile;
	private static String axiomConfigFile;
	private static Integer maxRetryCount;
	private static Integer retryIntervalSec;
	private static Integer batchSize;
	private static List<String> trackingElements;
	private static String[] DEFAULT_TRACKING_ELEMENTS = {"HASH", "SEQID", "MSGTYPE", "RULEID", "RECIPIENT"};
	
	private static Logger logger = Logger.getLogger(AxiomRetryConfig.class);

	public static void loadConfig() throws Exception {
		Properties fileProps = new Properties();
		String configFile = System.getProperty("configFile");
		if(configFile == null) 
			throw new Exception("Value for property -DconfigFile not specified");
					
		logger.info(String.format("Notification Config File Path : %s", configFile));
		try (InputStream input = new FileInputStream(configFile)) {
			fileProps.load(input);
		} catch (IOException e) {
			logger.error(String.format("Exception occurred while reading file at Location: %s", configFile), e);
			throw e; 
		} 			
		
		kafkaConfigFile = fileProps.getProperty("kafkaConfigFile");
		dBConfigFile = fileProps.getProperty("dBConfigFile");
		axiomConfigFile = fileProps.getProperty("axiomConfigFile");
		maxRetryCount = Integer.valueOf(fileProps.getProperty("maxRetryCount", "3"));
		retryIntervalSec = Integer.valueOf(fileProps.getProperty("retryIntervalSec", "10"));
		batchSize = Integer.valueOf(fileProps.getProperty("batchSize", "500"));
		String trkElem = fileProps.getProperty("trackingElements");
		trackingElements = Arrays.asList(trkElem != null ? trkElem.split(",") : DEFAULT_TRACKING_ELEMENTS);
		trackingElements.forEach(x -> x.trim());
		logger.info(String.format("Notification Config File Loaded Successfully %s", fileProps));

	}

	public static String getKafkaConfigFile() {
		return kafkaConfigFile;
	}

	public static String getdBConfigFile() {
		return dBConfigFile;
	}

	public static String getAxiomConfigFile() {
		return axiomConfigFile;
	}

	public static Integer getMaxRetryCount() {
		return maxRetryCount;
	}

	public static Integer getRetryIntervalSec() {
		return retryIntervalSec;
	}

	public static Integer getBatchSize() {
		return batchSize;
	}

	public static List<String> getTrackingElements() {
		return trackingElements;
	}


}
