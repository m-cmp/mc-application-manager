package kr.co.mcmp.softwarecatalog.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Restart reasons")
public enum RestartReasonType implements LabeledEnum {
	PERFORMANCE_ISSUE("performance_issue", "Performance Issue", "Restart due to performance issues"),
	MEMORY_LEAK_DETECTED("memory_leak_detected", "Memory Leak Detected", "Restart due to detected memory leak"),
	CONFIGURATION_UPDATE("configuration_update", "Configuration Update", "Restart due to configuration changes"),
	SCHEDULED_MAINTENANCE("scheduled_maintenance", "Scheduled Maintenance", "Restart for scheduled maintenance"),
	USER_REQUEST("user_request", "User Request", "Restart requested by user"),
	APPLICATION_CRASH("application_crash", "Application Crash", "Restart due to application crash"),
	SECURITY_PATCH("security_patch", "Security Patch", "Restart to apply security patch"),
	RESOURCE_EXHAUSTION("resource_exhaustion", "Resource Exhaustion", "Restart due to resource exhaustion");

	private final String value;
	private final String label;
	private final String description;

	RestartReasonType(String value, String label, String description) {
		this.value = value;
		this.label = label;
		this.description = description;
	}

	@Override
	public String getValue() { return value; }
	@Override
	public String getLabel() { return label; }
	@Override
	public String getDescription() { return description; }
}
