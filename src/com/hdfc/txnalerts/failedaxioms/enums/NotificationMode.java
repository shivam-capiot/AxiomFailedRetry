package com.hdfc.txnalerts.failedaxioms.enums;

public enum NotificationMode {
	
	SMS("S"), MAIL("E");
	
	private String notificationMode;
	
	private NotificationMode(String notifyMode) {
		notificationMode = notifyMode;
	}

	public String getValue() {
		return notificationMode;
	}
	
	public static NotificationMode forString(String notificationMode) {
		NotificationMode[] notificationModes = NotificationMode.values();
		for (NotificationMode notifyMode : notificationModes) {
			if (notificationMode.equalsIgnoreCase(notifyMode.getValue())) {
				return notifyMode;
			}
		}
		return null;
	}
	
	
}
