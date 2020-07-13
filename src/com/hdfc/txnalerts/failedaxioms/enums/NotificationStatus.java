package com.hdfc.txnalerts.failedaxioms.enums;

public enum NotificationStatus {
	
	SUCCESS("Success"), FAILEDTOBERETRIED("Failed - To be retried"), FAILED("Failed");
	
	private String notificationStatus;
	
	private NotificationStatus(String notificationStatus) {
		this.notificationStatus = notificationStatus;
	}

	public String toString() {
		return notificationStatus;
	}
	
	public static NotificationStatus forString(String notificationStatus) {
		NotificationStatus[] notificationStatuses = NotificationStatus.values();
		for (NotificationStatus notifyStatus : notificationStatuses) {
			if (notificationStatus.equalsIgnoreCase(notifyStatus.toString())) {
				return notifyStatus;
			}
		}
		return null;
	}

}
