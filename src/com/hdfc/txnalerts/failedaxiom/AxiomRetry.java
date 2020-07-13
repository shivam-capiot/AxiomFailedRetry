package com.hdfc.txnalerts.failedaxiom;

import java.io.IOException;
import java.io.Reader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.w3c.dom.Element;

import com.hdfc.txnalerts.failedaxiom.config.AxiomConfig;
import com.hdfc.txnalerts.failedaxiom.config.AxiomRetryConfig;
import com.hdfc.txnalerts.failedaxiom.config.DbConfig;
import com.hdfc.txnalerts.failedaxiom.config.KafkaConfig;
import com.hdfc.txnalerts.failedaxiom.config.ServiceConfig;
import com.hdfc.txnalerts.failedaxiom.utils.Constants;
import com.hdfc.txnalerts.failedaxiom.utils.HTTPServiceConsumer;
import com.hdfc.txnalerts.failedaxiom.utils.TrackingContext;
import com.hdfc.txnalerts.failedaxiom.utils.Utils;
import com.hdfc.txnalerts.failedaxiom.utils.xml.XMLTransformer;
import com.hdfc.txnalerts.failedaxiom.utils.xml.XMLUtils;
import com.hdfc.txnalerts.failedaxioms.enums.NotificationMode;
import com.hdfc.txnalerts.failedaxioms.enums.NotificationStatus;

public class AxiomRetry implements Constants {

	private static Logger logger = Logger.getLogger(AxiomRetry.class);
	protected static final String dateFormat = "yyyy-MM-dd HH:mm:ss";
	protected static final String updateQuery = String.format("UPDATE %s SET FLAG = ?,RETRYCOUNT = ?,MODIFIEDTS = ? WHERE HASH = ? AND SEQID = ? AND RECIPIENT = ? AND MSGTYPE = ?", FAILED_AXIOM_TABLE);
	protected static final String selectQuery = String.format("select * from %s where FLAG = ? AND MODIFIEDTS < ? AND RETRYCOUNT < ? AND rownum <= ? ORDER BY INSERTTS", FAILED_AXIOM_TABLE);
	private static volatile boolean isInteruppted = false;
	
	static {
		// Load Property files before starting application
		try {
			// Always load this first as it holds information regarding other file locations
			AxiomRetryConfig.loadConfig();
			KafkaConfig.loadConfig();
			AxiomConfig.loadConfig();
			DbConfig.loadConfig();

		} catch (Exception e) {
			logger.error("Error occurred in Loading Config Files", e);
			throw new RuntimeException(e.getMessage());
		}
	}

	public static void main(String args[]) throws Exception {

		final Thread mainThread = Thread.currentThread();
		KafkaProducer<String, String> producer = new KafkaProducer<>(KafkaConfig.getProducerProps());
		//Added a shutDownHook to exit cleanly
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				logger.info("Interrupting main thread");
				isInteruppted = true;
				try {
					mainThread.join();
				} catch (InterruptedException e) {
					logger.error("ShutDown thread interrupted.....", e);
				}
				producer.close();
				logger.info(".......Exiting cleanly.....");
			}
		});
				
		fetchFromDB(producer);
	}

	protected static String getMsgId(JSONObject kafkaMsg, String recipient) {

		SimpleDateFormat msgIdDateformat = new SimpleDateFormat("ddMMyyyyHHmmssSSS");
		msgIdDateformat.setTimeZone(TimeZone.getTimeZone("IST"));
		StringBuilder msgIdBuilder = new StringBuilder();
		String seqNumber = kafkaMsg.optString("terminalTransactionSequenceNumber");
		if (Utils.isStringNotNullAndNotEmpty(seqNumber)) {
			msgIdBuilder.append(seqNumber);
		}
		if (Utils.isStringNotNullAndNotEmpty(recipient)) {
			msgIdBuilder.append(recipient);
		}
		msgIdBuilder.append(msgIdDateformat.format(new Date()));
		return msgIdBuilder.toString();
	}

	protected static void fetchFromDB(KafkaProducer<String, String> producer) {

		ServiceConfig serviceConfig = AxiomConfig.getOperationConfig(AXIOM_SMS_SERVICE);
		int maxRetryCount = AxiomRetryConfig.getMaxRetryCount();
		int retryIntervalCount = AxiomRetryConfig.getRetryIntervalSec();
		int batchSize = AxiomRetryConfig.getBatchSize();
		
		while (!isInteruppted) {

			try (Connection conn = DbConfig.getDBConnection()) {

				PreparedStatement selectStatement = conn.prepareStatement(selectQuery);
				Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("IST"));
				cal.setTime(new Date());
				cal.add(Calendar.SECOND, -retryIntervalCount);
				selectStatement.setString(1, "N");
				selectStatement.setTimestamp(2, new Timestamp(cal.getTime().getTime()));
				selectStatement.setInt(3, maxRetryCount);
				selectStatement.setInt(4, batchSize);
				
				ResultSet rs = selectStatement.executeQuery();

				while (rs.next() && !isInteruppted) {
					NotificationStatus status = null;
					String failureReason = null;
					try {
					
						TrackingContext.setTrackingContext(rs);
						String processFlag = rs.getString("FLAG");
						String alertType = rs.getString("ALERTTYPE");
						String recipient = rs.getString("RECIPIENT");
						String cardType = rs.getString("CARDTYPE");
						
						Timestamp insertDate = rs.getTimestamp("INSERTTS");
						Timestamp txnTime  = rs.getTimestamp("TXNTIME"); 
						Integer lastRetryCount = rs.getInt("RETRYCOUNT");
						logger.info(String.format("Retry Attempt No.: %d", lastRetryCount+1));

						NotificationMode notificationMode = NotificationMode.forString(alertType);
						Clob reqElemClob = rs.getClob("REQBODY");
						StringBuffer reqElemStr = getString(reqElemClob);
						
						JSONObject kafkaMsg = new JSONObject();
						Element reqElem = XMLTransformer.toXMLElement(reqElemStr.toString());
						Element smsBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./soapenv:Body/sms:SMSRequest");
						String msgId = getMsgId(kafkaMsg, recipient);
						Date modifyTime = new Date();
						XMLUtils.setValueAtXPath(smsBodyElem, "./submitdate", new SimpleDateFormat(dateFormat).format(modifyTime));
						XMLUtils.setValueAtXPath(smsBodyElem, "./msgid", msgId);

						kafkaMsg.put("cardNumber", rs.getString("HASH"));
						kafkaMsg.put("terminalTransactionSequenceNumber", rs.getString("SEQID"));
						kafkaMsg.put(RULEID, rs.getString("RULEID"));
						kafkaMsg.put("txnType", rs.getString("TXNTYPE"));
						kafkaMsg.put("messageType", rs.getString("MSGTYPE"));
						
						kafkaMsg.put("custom_transactionTime", new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date(txnTime.getTime())));
						kafkaMsg.put("axiomRequest", XMLTransformer.toString(reqElem));
						kafkaMsg.put("insertTime", new SimpleDateFormat(dateFormat).format(new Date(insertDate.getTime())));

						long startTime = System.currentTimeMillis();
						Element resElem = HTTPServiceConsumer.consumeXMLService(notificationMode.toString(),
								serviceConfig.getServiceURL(), serviceConfig.getHttpHeaders(),
								serviceConfig.getHttpMethod(), serviceConfig.getServiceTimeoutMillis(), reqElem);
						long endTime = System.currentTimeMillis();

						kafkaMsg.put("axiomResponse", XMLTransformer.toString(resElem));
						kafkaMsg.put("responseTime", String.format("%s ms", (endTime - startTime)));

						if (resElem == null) {
							logger.warn("Null response received from AXIOM");
							status = NotificationStatus.FAILEDTOBERETRIED;
							failureReason = "Null response from AXIOM";
							processFlag = "N";
						}

						String responseCode = XMLUtils.getValueAtXPath(resElem, "./soapenv:Body/sms:SMSResponse/responsecode").trim();
						String axiomMessage = XMLUtils.getValueAtXPath(resElem, "./soapenv:Body/sms:SMSResponse/message").trim();
						if ("-99".equals(responseCode)) {
							logger.warn("Failure response received from AXIOM");
							status = NotificationStatus.FAILEDTOBERETRIED;
							failureReason = String.format("Failure Response - %s", axiomMessage);
							processFlag = "N";
						}

						if ("00".equals(responseCode)) {
							if (axiomMessage.startsWith("APP")) {
								status = NotificationStatus.SUCCESS;
								logger.info("Success response received from AXIOM");
								processFlag = "Y";
							} else {
								//This will occur in case of request validation error
								//Ideally this should never occur in a retry scenario
								status = NotificationStatus.FAILED;
								failureReason = String.format("Error Response - %s", axiomMessage);
								logger.warn("Error response received from AXIOM");
								processFlag = "F";
							}
						}
						kafkaMsg.put("status", status.toString());
						kafkaMsg.put("retryCount", lastRetryCount+1);
						kafkaMsg.put("failureReason", failureReason);
						updateIntoDB(kafkaMsg, reqElem, processFlag, modifyTime, lastRetryCount+1, recipient);
						sendToProcessedTopic(cardType, kafkaMsg, producer);

					} catch (Exception e) {
						logger.error("Exception occurred", e);
					} finally {
						TrackingContext.clear();
					}
				}
			} catch (Exception e) {
				logger.error("Unable to fetch records from DB ", e);
				//Probably due to connectivity issue to DB
				//Retry after some time
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e1) {}
			}
		}
	}

	protected static void updateIntoDB(JSONObject kafkaMsg, Element reqElem, String processFlag, Date modifyTime, Integer retryCount, String recipient) {

		try (Connection conn = DbConfig.getDBConnection()) {

			PreparedStatement updateStatement = conn.prepareStatement(updateQuery);
			updateStatement.setString(1, processFlag);
			updateStatement.setInt(2, retryCount);
			updateStatement.setTimestamp(3, new Timestamp(modifyTime.getTime()));
			updateStatement.setString(4, kafkaMsg.getString("cardNumber"));
			updateStatement.setString(5, kafkaMsg.optString("terminalTransactionSequenceNumber"));
			updateStatement.setString(6, recipient);
			updateStatement.setString(7, kafkaMsg.getString("messageType"));
			int count = updateStatement.executeUpdate();	
			logger.debug(String.format("No. of rows updated -%d", count));
		} catch (Exception e) {
			logger.error("Unable to update record into DB ", e);
		}
	}

	protected static void sendToProcessedTopic(String cardType, JSONObject reqResObj, KafkaProducer<String, String> producer) throws Exception {
		
		String topicName;
		if("DC".equalsIgnoreCase(cardType))
			topicName = KafkaConfig.getDCProcessedProducerTopic();
		else if ("CC".equalsIgnoreCase(cardType))
			topicName = KafkaConfig.getCCProcessedProducerTopic();
		else
			throw new Exception("Invalid cardType");
		
		Utils.sendRecordToTopic(new ProducerRecord<String, String>(topicName, reqResObj.toString()), producer);
	}

	private static StringBuffer getString(Clob reqElemClob) throws SQLException, IOException {
		Reader r = reqElemClob.getCharacterStream();
		StringBuffer reqElemStr = new StringBuffer();
		int ch;
		while ((ch = r.read()) != -1) {
			reqElemStr.append("" + (char) ch);
		}
		return reqElemStr;
	}

}