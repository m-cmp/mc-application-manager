package kr.co.mcmp.softwarecatalog.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "General operation reasons")
public enum ReasonType implements LabeledEnum {
	PERFORMANCE_ISSUES("performance_issues", "Performance Issues", "Operations due to performance problems"),
	SECURITY_VULNERABILITIES("security_vulnerabilities", "Security Vulnerabilities", "Operations due to security vulnerabilities"),
	COST_OPTIMIZATION("cost_optimization", "Cost Optimization", "Operations for cost optimization"),
	MAINTENANCE("maintenance", "Maintenance", "Regular maintenance operations"),
	USER_REQUEST("user_request", "User Request", "Operations requested by users"),
	SYSTEM_ERROR("system_error", "System Error", "Operations due to system errors"),
	RESOURCE_CONSTRAINTS("resource_constraints", "Resource Constraints", "Operations due to resource limitations"),
	COMPLIANCE_REQUIREMENTS("compliance_requirements", "Compliance Requirements", "Operations for compliance requirements");

	private final String value;
	private final String label;
	private final String description;

	ReasonType(String value, String label, String description) {
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
