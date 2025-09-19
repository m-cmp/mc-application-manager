package kr.co.mcmp.softwarecatalog.dto;

public class SelectBoxOptionDTO {
	private String value;
	private String label;
	private String description;

	public SelectBoxOptionDTO() {}

	public SelectBoxOptionDTO(String value, String label, String description) {
		this.value = value;
		this.label = label;
		this.description = description;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
