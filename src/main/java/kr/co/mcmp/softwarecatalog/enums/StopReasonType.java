package kr.co.mcmp.softwarecatalog.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stop reasons")
public enum StopReasonType implements LabeledEnum {
	RESOURCE_OPTIMIZATION("resource_optimization", "Resource Optimization", "Stop for resource optimization"),
	MAINTENANCE_WINDOW("maintenance_window", "Maintenance Window", "Stop during maintenance window"),
	SECURITY_UPDATE_REQUIRED("security_update_required", "Security Update Required", "Stop for security updates"),
	APPLICATION_NOT_NEEDED("application_not_needed", "Application Not Needed", "Application no longer needed"),
	PERFORMANCE_DEGRADATION("performance_degradation", "Performance Degradation", "Stop due to performance degradation"),
	COST_REDUCTION("cost_reduction", "Cost Reduction", "Stop for cost reduction"),
	SYSTEM_SHUTDOWN("system_shutdown", "System Shutdown", "Stop due to system shutdown"),
	EMERGENCY_STOP("emergency_stop", "Emergency Stop", "Emergency stop due to critical issues");

	private final String value;
	private final String label;
	private final String description;

	StopReasonType(String value, String label, String description) {
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
