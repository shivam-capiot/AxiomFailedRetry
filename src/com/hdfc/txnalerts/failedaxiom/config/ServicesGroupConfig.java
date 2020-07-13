package com.hdfc.txnalerts.failedaxiom.config;

import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.hdfc.txnalerts.failedaxiom.utils.Constants;
import com.hdfc.txnalerts.failedaxiom.utils.CryptoUtil;
import com.hdfc.txnalerts.failedaxiom.utils.HTTPServiceConsumer;
import com.hdfc.txnalerts.failedaxiom.utils.Utils;

public class ServicesGroupConfig implements Constants {
	
	private static final Logger logger = Logger.getLogger(ServicesGroupConfig.class);
	private String mServiceGroupName;
	private String mServiceBaseURL;
	private Long mReqTimeoutMillis;
	private Map<String, String> mHttpHeaders;
	private Proxy mHttpProxy;
	
	private Map<String, ServiceConfig> mServicesConfig;

	public ServicesGroupConfig(String groupName, JSONObject grpConfigJson) {
		this(groupName, grpConfigJson, ServiceConfig.class);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public ServicesGroupConfig(String groupName, JSONObject grpConfigJson, Class svcConfigClass) {
		mServiceGroupName = groupName;
		mServiceBaseURL = grpConfigJson.optString(CONFIG_PROP_SERVICE_BASE_URL);
		try { 
			new URL(mServiceBaseURL);
		}
		catch (MalformedURLException mux) {
			logger.warn(String.format("The service group base URL <%s> is not valid. Error: <%s>", mServiceBaseURL, mux));
		}
		mHttpHeaders = ServicesGroupConfig.loadHttpHeaders(grpConfigJson);
		mHttpProxy = ServicesGroupConfig.loadHttpProxy(grpConfigJson);
		mReqTimeoutMillis = grpConfigJson.optLong(CONFIG_PROP_SERVICE_TIMEOUT_MILLIS, HTTPServiceConsumer.DEFAULT_SERVICE_TIMEOUT_MILLIS);
		try { 
			Constructor svcCfgConstructor = svcConfigClass.getConstructor(ServicesGroupConfig.class, JSONObject.class);
			mServicesConfig = new HashMap<String, ServiceConfig>();
			JSONArray servicesConfig = grpConfigJson.optJSONArray(CONFIG_PROP_SERVICES);
			if (servicesConfig != null) {
				for (int i=0; i<servicesConfig.length(); i++) {
					JSONObject serviceConfig = servicesConfig.getJSONObject(i);
					ServiceConfig svcConfig = (ServiceConfig) svcCfgConstructor.newInstance(this, serviceConfig);
					mServicesConfig.put(svcConfig.getTypeName(), svcConfig);
				}
			}
		}
		catch (Exception x) {
			logger.warn(String.format("An error occurred when loading services configuration for group %s", mServiceGroupName), x);
		}
	}
	
	public Map<String, String> getHttpHeaders() {
		return mHttpHeaders;
	}
	
	Proxy getHttpProxy() {
		return mHttpProxy;
	}

	String getServiceBaseURL() {
		return mServiceBaseURL;
	}

	String getServiceGroupName() {
		return mServiceGroupName;
	}
	
	public Long getReqTimeoutMillis() {
		return mReqTimeoutMillis;
	}

	public ServiceConfig getServiceConfig(String typeName) {
		return mServicesConfig.get(typeName);
	}

	public static Proxy loadHttpProxy(JSONObject grpConfigObj) {
		if (grpConfigObj == null) {
			return null;
		}
		
		JSONObject httpProxyObj = (JSONObject) grpConfigObj.optJSONObject(CONFIG_PROP_HTTP_PROXY);
		if (httpProxyObj == null) {
			return null;
		}
		
		String httpProxyServer = httpProxyObj.optString(CONFIG_PROP_HTTP_PROXY_SERVER);
		int httpProxyPort = httpProxyObj.optInt(CONFIG_PROP_HTTP_PROXY_PORT, 80);
		
		return (httpProxyServer != null && httpProxyServer.isEmpty() == false && httpProxyPort > 0) ? new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpProxyServer, httpProxyPort)) : null;
	}

	public static Map<String, String> loadHttpHeaders(JSONObject grpConfigObj) {
		if (grpConfigObj == null) {
			return null;
		}
		
		JSONArray httpHeadersArr = new JSONArray();
		Object httpHeadersObj = grpConfigObj.opt(CONFIG_PROP_HTTP_HEADERS);
		if (httpHeadersObj != null && httpHeadersObj instanceof JSONArray) {
			JSONArray hdrsList = (JSONArray) httpHeadersObj;
			for (int i=0; i<hdrsList.length(); i++) {
				Object hdrObj = hdrsList.get(i);
				if (hdrObj instanceof JSONObject) {
					httpHeadersArr.put((JSONObject) hdrObj);
				}
			}
		}
		
		if (httpHeadersArr == null || httpHeadersArr.length() == 0) {
			return null;
		}
		
		Map<String, String> httpHeaders = new HashMap<String, String>();
		for (int i=0; i< httpHeadersArr.length(); i++) {
			JSONObject httpHeaderJson = httpHeadersArr.getJSONObject(i);
			String httpHeader = httpHeaderJson.optString(CONFIG_PROP_HEADER);
			if (httpHeader == null || httpHeader.isEmpty()) {
				continue;
			}
			
			Object valObj = httpHeaderJson.opt(CONFIG_PROP_VALUE);
			if (valObj == null) {
				continue;
			}
			
			if (HTTP_HEADER_AUTHORIZATION.equals(httpHeader)) {
				if (valObj instanceof JSONObject) { 
					JSONObject valDoc = (JSONObject) valObj;
					String authVal = getAuthorizationVal(valDoc);
					
					if (Utils.isStringNotNullAndNotEmpty(authVal)) {
						httpHeaders.put(HTTP_HEADER_AUTHORIZATION, authVal);
					}
				}
			}
			else {
				httpHeaders.put(httpHeader, valObj.toString());
			}
		}
		
		return (httpHeaders.size() > 0) ? httpHeaders : null;
	}

	private static String getAuthorizationVal(JSONObject valObj) {
		
		String authType = (String) valObj.optString(CONFIG_PROP_TYPE, "Basic");
		switch(authType) {
		case "Basic":{
			String userID = valObj.optString(CONFIG_PROP_USERID);
			String password = valObj.optString(CONFIG_PROP_PASSWORD);
			if (Utils.isStringNotNullAndNotEmpty(userID) && Utils.isStringNotNullAndNotEmpty(password)) {
				return String.format("%s %s", authType, Base64.getEncoder().encodeToString(userID.concat(":").concat(CryptoUtil.decrypt(password)).getBytes()));
			}
		}
		case "OAuth 2.0":{
			String token = valObj.getString("token");
			if(Utils.isStringNotNullAndNotEmpty(token))
				return String.format("%s %s", "Bearer", token);
		}
		default:return "";
		}
	}

}
