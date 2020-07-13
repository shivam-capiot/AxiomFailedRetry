package com.hdfc.txnalerts.failedaxiom.config;

import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.w3c.dom.Element;

import com.hdfc.txnalerts.failedaxiom.utils.Constants;
import com.hdfc.txnalerts.failedaxiom.utils.HTTPServiceConsumer;
import com.hdfc.txnalerts.failedaxiom.utils.Utils;
import com.hdfc.txnalerts.failedaxiom.utils.xml.XMLTransformer;

public class ServiceConfig implements Constants {

	private String mTypeName;

	private URI mServiceURI;
	private String mReqJSONShell;
	private Element mReqXMLShell;
	private long mReqTimeoutMillis;
	private String mHttpMethod;
	private Map<String, String> mHttpHeaders;
	private Proxy mHttpProxy;
	
	private static final Logger logger = Logger.getLogger(ServiceConfig.class);
	
	public ServiceConfig(ServicesGroupConfig svcsGrpCfg, JSONObject serviceConfig) {
		mTypeName = serviceConfig.optString(CONFIG_PROP_TYPE);
		mReqJSONShell = serviceConfig.optString(Constants.CONFIG_PROP_REQ_JSON_SHELL);
		mReqXMLShell = XMLTransformer.fromEscapedString(serviceConfig.optString(CONFIG_PROP_REQ_XML_SHELL));
		String svcBaseURIStr = (svcsGrpCfg != null && svcsGrpCfg.getServiceBaseURL() != null) ? svcsGrpCfg.getServiceBaseURL() : "";
		String svcURIStr = (String) serviceConfig.optString(CONFIG_PROP_SERVICE_URL, "");
		try {
			URI svcUri = new URI(svcURIStr);
			mServiceURI = (svcUri.isAbsolute()) ? svcUri :  new URI(svcBaseURIStr).resolve(svcUri);
		}
		catch (Exception x) {
			logger.warn(String.format("Error occurred while initializing service URL for operation %s. Service base URL is <%s> and service URL is <%s>. Error: <%s>", mTypeName, svcBaseURIStr, svcURIStr, x));
		}

		mReqTimeoutMillis = serviceConfig.optLong(CONFIG_PROP_SERVICE_TIMEOUT_MILLIS, svcsGrpCfg.getReqTimeoutMillis());
		String httpMthd = serviceConfig.optString(CONFIG_PROP_SERVICE_HTTP_METHOD);
		mHttpMethod = (Utils.isStringNotNullAndNotEmpty(httpMthd)) ? httpMthd : HTTPServiceConsumer.HTTP_METHOD_POST;
		
		Map<String, String> serviceHttpHeaders = ServicesGroupConfig.loadHttpHeaders(serviceConfig);
		mHttpHeaders = (serviceHttpHeaders != null) ? serviceHttpHeaders : ((svcsGrpCfg != null && svcsGrpCfg.getHttpHeaders() != null) ? svcsGrpCfg.getHttpHeaders() : null);
		
		Proxy serviceHttpProxy = ServicesGroupConfig.loadHttpProxy(serviceConfig);
		mHttpProxy = (serviceHttpProxy != null) ? serviceHttpProxy : ((svcsGrpCfg != null && svcsGrpCfg.getHttpProxy() != null) ? svcsGrpCfg.getHttpProxy() : null);
	}
	
	public String getOperationName() {
		return getTypeName();
	}
	
	public String getRequestJSONShell() {
		return mReqJSONShell;
	}
	
	public Element getRequestXMLShell() {
		return mReqXMLShell;
	}

	public Map<String, String> getHttpHeaders() {
		return mHttpHeaders;
	}
	
	public String getHttpMethod() {
		return mHttpMethod;
	}

	public Proxy getHttpProxy() {
		return mHttpProxy;
	}
	
	public long getServiceTimeoutMillis() {
		return mReqTimeoutMillis;
	}
	
	public URL getServiceURL() {
		if (mServiceURI != null) {
			try {
				return mServiceURI.toURL();
			}
			catch (Exception x) {
				logger.warn(String.format("An error occurred while setting path parameters of url %s", mServiceURI.toString()));
			}
		}
		return null;
	}

	public String getTypeName() {
		return mTypeName;
	}
	
	protected void setRequestXMLShell(Element reqXMLShell) {
		mReqXMLShell = reqXMLShell;
	}
	

	
}
