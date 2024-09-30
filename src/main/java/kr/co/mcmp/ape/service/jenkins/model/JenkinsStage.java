package kr.co.mcmp.ape.service.jenkins.model;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class JenkinsStage {

    private String id;

    private String name;

    private String status;

    private Long startTimeMillis;

    private Long endTimeMillis;

    private Long pauseDurationMillis;

    private Long durationMillis;
    
//    private Link _links;
//
//	@Getter
//	@ToString
//    public static class Link {
//	   
//		private Self self;
//	
//		@Getter
//		@ToString
//		public static class Self {
//			private String href;
//		}
//    }
}
