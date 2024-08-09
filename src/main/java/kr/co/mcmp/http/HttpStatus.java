package kr.co.mcmp.http;

/**
 * HTTP Response Code 정의
 * @author hclee 
 *
 */
public enum HttpStatus {
	
	NO_ERROR(2200, "No Error"),
	LOGIN_FAIL(2501, "ID or Password Invalid"),
	BAD_REQUEST(2400, "Bad Request"),
	AUTHORIZED_REQUIRED(2401, "Authorized Required"),	
	UNAUTHORIZED(2402, "Unauthorized"),	
	SERVICE_NOT_FOUND(2404, "Service not found"),	
	SERVICE_DISABLED(2403, "Service disabled"),	
	INCORRECT_PASSWORD(2405, "Incorrect password"),
	BUILD_NOT_FOUND(2406, "Build not found"),	
	LOGIN_REQUIRED(2407, "Login Required"),	
	REQUEST_TIMEOUT(2408, "Request Timeout"),	
	INTERNAL_SERVER_ERROR(2500, "Internal server error"),
	DATA_FORMAT_ERROR(2501, "Invalid data format"),
	SERVICE_ERROR_PROJECT_NOT_FOUND(2601, "Project not found"),
	SERVICE_ERROR_USER_NOT_FOUND(2602, "User not found"),
	SERVICE_ERROR_PROJECT_CREATION_FAIL(2603, "Project creation fail"),
	SERVICE_ERROR_REPOSITORY_NOT_FOUND(2604, "Project not found"),
	SERVICE_ERROR_DOCKER_FILE_CREATION_FAIL(2605, "Dockerfile creation failed"),
	SERVICE_ERROR_JENKINS_JOB_CREATION_FAIL(2606, "Jenkins job creation failed"),
	SERVICE_ERROR_HARBOR_CREATION_FAIL(2607, "Harbor Repository creation failed"),
//	SERVICE_ERROR_HARBOR_HELM_CHART_URL_NOT_FOUND(2608, "Harbor Helm Chart Repository could not found."),
	SERVICE_ERROR_NEXUS_HELM_CHART_URL_NOT_FOUND(2608, "Nexus Repository could not found."),
	SERVICE_ERROR_HELM_CHART_CONNECT_FAIL(2609, "Helm Repository could not connect."),
	SERVICE_ERROR_APPLICATION_ALREADY_EXISTED(2610, "Application is already exists."),
	SERVICE_ERROR_APPLICATION_CREATION_FAIL(2611, "Application creation failed."),
	SERVICE_ERROR_APPLICATION_IS_NOT_EXISTS(2615, "Application is not exists."),
	SERVICE_ERROR_APPLICATION_UPDATE_FAIL(2616, "Application modification failed."),

	SERVICE_ERROR_SOURCE_BRANCH_NOT_FOUND(2612, "Have not sourceBranch"),
	SERVICE_ERROR_TARGET_BRANCH_NOT_FOUND(2613, "Have not targetBranch"),
	SERVICE_ERROR_MERGE_ACCEPT(2614, "Merge Request Success, Merge Accept Fail"),
	
	SERVICE_ERROR_BUILD_OVERLAP(2618, "Build insert overlap"),
	SERVICE_ERROR_BUILD_FAILURE(2619, "Build Failure"),

	CONFIG_NOT_FOUND(2701, "Config not found"),
	CONFIG_IS_INVALID(2702, "Config is invalid"),
	CAN_NOT_DELETE_DEPLOY(2801, "Can't delete deploy"),
	GROUP_NAME_OVERLAP(4001, "Group name overlap"),
	GROUP_ALREAY_EXIST(4002, "Group already exist"),
	GROUP_NOT_EXIST(4003, "Group not exist");

	
	
	
	private final int value;
	private final String reasonPhraseCode;


	HttpStatus(int value, String reasonPhraseCode) {
		this.value = value;
		this.reasonPhraseCode = reasonPhraseCode;
	}


	public int value() {
		return this.value;
	}

	public String getReasonPhraseCode() {
		return this.reasonPhraseCode;
	}
 
	
	//@Nullable
	public static HttpStatus resolve(int statusCode) {
		for (HttpStatus status : values()) {
			if (status.value == statusCode) {
				return status;
			}
		}
		return null;
	}

}