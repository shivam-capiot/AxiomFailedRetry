package com.hdfc.txnalerts.failedaxiom.utils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.MDC;

import com.hdfc.txnalerts.failedaxiom.config.AxiomRetryConfig;

public class TrackingContext {
	private List<SimpleEntry<String, String>> mTrackVals = new ArrayList<SimpleEntry<String, String>>();
	private String mTrackParamsStr;
	private static Map<Long, TrackingContext> mTrackCtx = new ConcurrentHashMap<Long, TrackingContext>();
	private static TrackingContext DEFAULT_TRACKING_CONTEXT = new TrackingContext();
	private static List<String> trackingElements = AxiomRetryConfig.getTrackingElements();
	
	private TrackingContext() {
		mTrackParamsStr = "";
	}

	private TrackingContext(ResultSet dbRecord) {
		
		StringBuilder strBldr = new StringBuilder();
		/*
		ResultSetMetaData rsmd = dbRecord.getMetaData();
		Map<String, Integer> trackingMap = IntStream.range(1, rsmd.getColumnCount()).boxed()
						.filter(cIdx -> isColumnInTrackingElements(cIdx, rsmd))
						.collect(Collectors.toMap(cIdx -> getColumnName(cIdx, rsmd), Function.identity()));*/	
		
		for(String trackingElement : trackingElements) {
			String value  = "";
			try {
				value = dbRecord.getObject(trackingElement).toString();
			} catch (Exception e) { }
			
			mTrackVals.add(new SimpleEntry<String, String>(trackingElement, value.trim()));
			strBldr.append(String.format("[%s: %s] ", trackingElement, value.trim()));
		}
		
		strBldr.setLength(strBldr.length() - 1);
		mTrackParamsStr = strBldr.toString();
	}

	private static String getColumnName(Integer cIdx, ResultSetMetaData rsmd) {
		try {
			return rsmd.getColumnName(cIdx).toUpperCase();
		} catch (SQLException e) {
			return "";
		}
	}
	
	private static boolean isColumnInTrackingElements(Integer cIdx, ResultSetMetaData rsmd) {
		
		try {
			return trackingElements.contains(rsmd.getColumnName(cIdx).toUpperCase());
		} catch (SQLException e) {
			return false;
		}
	}

	public static void setTrackingContext(ResultSet dbRecord) throws SQLException { 
		TrackingContext trkCtx = new TrackingContext(dbRecord);
		MDC.put("trkctx", trkCtx.toString());
		mTrackCtx.put(Thread.currentThread().getId(), trkCtx);
	}

	public String toString() {
		return mTrackParamsStr;
	}

	public static void clear() {
		mTrackCtx.remove(Thread.currentThread().getId());
		MDC.clear();
	}
	
	public static TrackingContext getTrackingContext() {
		TrackingContext trackCtx = mTrackCtx.get(Thread.currentThread().getId());
		return (trackCtx != null) ? trackCtx : DEFAULT_TRACKING_CONTEXT;
	}

	public static void duplicateContextFromThread(long sourceThreadID) {
		TrackingContext trkngCtx = mTrackCtx.get(sourceThreadID);
		if (trkngCtx != null) {
			mTrackCtx.put(Thread.currentThread().getId(), trkngCtx);
		}
	}
}
