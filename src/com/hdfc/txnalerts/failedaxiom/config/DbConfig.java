package com.hdfc.txnalerts.failedaxiom.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.hdfc.txnalerts.failedaxiom.utils.Utils;

public class DbConfig {

	private static Logger logger = Logger.getLogger(DbConfig.class);
	private static String url;
	private static String userName;
	private static String password ;
	private static String driverClass;
	private static String schemaName;
	private static Connection conn = null;
	
	public static void loadConfig(JSONObject dbConfigJson) {
		
		logger.info("Loading DB Config");
		
		url = dbConfigJson.getString("url");
		userName = dbConfigJson.getString("userName");
		password = dbConfigJson.getString("password");
		driverClass = dbConfigJson.getString("driverClass");
		schemaName = dbConfigJson.optString("schemaName", userName);
		logger.info("Loaded DB Config successfully");
		
	}
	
	private static void openConnection() throws Exception {
		try {
			Class.forName(driverClass); 
			conn = DriverManager.getConnection(url, userName, password);
			conn.setSchema(schemaName);
			
			logger.info(String.format("DB Connection established successfully %s ", conn));
		}catch (Exception e) {
			logger.error("Exception occurred while establishing database connection ", e);
			throw e;
		}
	}
	
	public static Connection getDBConnection() throws Exception {
		if(conn == null || conn.isClosed()) {
			openConnection();
		}
		return conn;
	}
	
	public static void loadConfig(Path filePath) throws Exception {
		String dbConfigStr = Utils.readFile(filePath.toString());
		loadConfig(new JSONObject(dbConfigStr));
	}
	
	public static void loadConfig(String dbJsonStr) {
		loadConfig(new JSONObject(dbJsonStr));
	}

	public static void loadConfig() throws Exception {
		loadConfig(Paths.get(AxiomRetryConfig.getdBConfigFile()));
	}

	public static String getUrl() {
		return url;
	}

	public static String getUserName() {
		return userName;
	}

	public static String getPassword() {
		return password;
	}

	public static String getDriverClass() {
		return driverClass;
	}

	public static String getSchemaName() {
		return schemaName;
	}

	public static void closeConnection() {
		try{
			if(conn != null && !conn.isClosed()) {
				conn.close();
				logger.info("DB connection Closed....");
			}
		} catch (Exception e) {
			logger.error("Error occurred in closing DB connection", e);
		}
	}
	

}
