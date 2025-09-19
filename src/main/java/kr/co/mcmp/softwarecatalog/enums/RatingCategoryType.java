package kr.co.mcmp.softwarecatalog.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Rating categories")
public enum RatingCategoryType implements LabeledEnum {
	PERFORMANCE("Performance", "Performance", "Performance-related evaluation"),
	SECURITY("Security", "Security", "Security-related evaluation"),
	USABILITY("Usability", "Usability", "Usability-related evaluation"),
	RELIABILITY("Reliability", "Reliability", "Reliability-related evaluation"),
	SUPPORT("Support", "Support", "Support-related evaluation"),
	DOCUMENTATION("Documentation", "Documentation", "Documentation quality evaluation"),
	COMMUNITY("Community", "Community", "Community support evaluation"),
	SCALABILITY("Scalability", "Scalability", "Scalability evaluation");

	private final String value;
	private final String label;
	private final String description;

	RatingCategoryType(String value, String label, String description) {
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
