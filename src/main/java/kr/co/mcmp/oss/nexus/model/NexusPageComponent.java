package kr.co.mcmp.oss.nexus.model;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@Tag(name = "NexusPageComponent", description = "넥서스 컴포넌트 페이지 객체")
public class NexusPageComponent implements Serializable {

	private static final long serialVersionUID = 6346599677940737012L;

	private	List<NexusComponent> items;
	private String	continuationToken;
}
