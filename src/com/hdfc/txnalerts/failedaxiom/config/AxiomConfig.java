package com.hdfc.txnalerts.failedaxiom.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.hdfc.txnalerts.failedaxiom.utils.Utils;
import com.hdfc.txnalerts.failedaxioms.enums.NotificationMode;

public class AxiomConfig {
	
	private static ServicesGroupConfig mAxiomSvsConfig; 
	private static boolean isSMSEnabled;
	private static boolean isMailEnabled;
	private static List<NotificationMode> notifyModes = new ArrayList<>(); 
	
	private static Logger logger = Logger.getLogger(AxiomConfig.class);
	
	public static void loadConfig(JSONObject axiomConfigJson) {
		logger.info("Loading Axiom Config");
		
		mAxiomSvsConfig = new ServicesGroupConfig("axiomConfig", axiomConfigJson.getJSONObject("serviceConfig"));
		isSMSEnabled = axiomConfigJson.optBoolean("enableSMS", true);
		isMailEnabled = axiomConfigJson.optBoolean("enableMail", true);
		
		if(isSMSEnabled)
			notifyModes.add(NotificationMode.SMS);
		if(isMailEnabled)
			notifyModes.add(NotificationMode.MAIL);
		
		logger.info("Successfully Loaded Axiom Config");
	}
	
	public static void loadConfig(Path filePath) throws Exception {
		String axiomConfigStr = Utils.readFile(filePath.toString());
		loadConfig(new JSONObject(axiomConfigStr));
	}
	
	public static void loadConfig(String axiomJsonStr) {
		loadConfig(new JSONObject(axiomJsonStr));
	}

	public static void loadConfig() throws Exception {
		loadConfig(Paths.get(AxiomRetryConfig.getAxiomConfigFile()));
	}
	
	public static ServicesGroupConfig getAxiomSvsConfig() {
		return mAxiomSvsConfig;
	}
	
	public static ServiceConfig getOperationConfig(String opName) {
		return mAxiomSvsConfig.getServiceConfig(opName);
	}

	public static boolean isSMSEnabled() {
		return isSMSEnabled;
	}

	public static boolean isMailEnabled() {
		return isMailEnabled;
	}

	public static List<NotificationMode> getNotificationModes() {
		return notifyModes;
	}


}
