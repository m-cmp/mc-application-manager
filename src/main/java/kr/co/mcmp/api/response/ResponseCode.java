package kr.co.mcmp.api.response;

import kr.co.mcmp.exception.McmpException;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public enum ResponseCode {

	OK(200, "정상처리 되었습니다."),

	/* common */
	BAD_REQUEST(400, "BAD REQUEST"),
	UNAUTHORIZED(401, "UNAUTHORIZED"),
	FORBIDDEN(403, "FORBIDDEN"),
	NOT_FOUND(404, "NOT FOUND"),
	METHOD_NOT_ALLOWED(405,"METHOD NOT ALLOWED"),
	CLIENT_ERROR(408,"CLIENT_ERROR"),
	CONFLICT(409,"CONFLICT"),
	INTERNAL_SERVER_ERROR(500,"INTERNAL SERVER ERROR"),
	
	/* 공통 코드 */
	COMMON_CODE_EXISTS(9001, "EXISTS COMMON CODE"),
	COMMON_CODE_DELETE_NOT_ALLOWED(9002,"DELETE NOT ALLOWED COMMON CODE"),

	/* Exception */
	UNKNOWN_ERROR(9999, "Unknown error"),
	
	/* GitLab-Project */
	ALREADY_EXISTS(1001, "ALREADY EXISTS"),
	CREATE_FAILED_PROJECT_SOURCE(1003, "CREATE FAILED PROJECT SOURCE"),

	/* Jenkins */
	CREATE_FAILED_JENKINS_JOB(1103, "CREATE FAILED JENKINS JOB"),
	EXISTS_JENKINS_JOB(1104, "EXISTS JENKINS JOB"),
	NOT_EXISTS_JENKINS_JOB(1105, "NOT EXISTS JENKINS JOB"),
	ERROR_JENKINS_API(1106, "ERROR JENKINS API"),
	
	/* Workload Deploy */
	RUN_FAILED_DEPLOY(1201, "RUN FAILED DEPLOY"),
	
	/* Catalog Deploy */
	CREATE_FAILED_APPLICATION(1301, "CREATE FAILED APPLICATION"),
	NOT_EXISTS_APPLICATION(1302, "NOT EXISTS APPLICATION"),
	EXISTS_APPLICATION(1303, "EXISTS APPLICATION"),
	
	/* K8S Client */
	CONNECTION_FAILED_CLUSTER(1401, "CONNECTION FAILED CLUSTER"),
	
	/* OSS */
	IN_USE_OSS(1501, "OSS IN USE CANNOT BE DELETED"),
	IS_NOT_MAPPED_OSS(1502, "IS NOT MAPPED OSS TO SERVICE GROUP.");	
	
	private final int code;
	private final String message;

	ResponseCode(int code, String message) {
		this.code = code;
		this.message = message;
	}
	
	private static final Map<Integer, ResponseCode> map =
			Stream.of(values()).collect(Collectors.toMap(ResponseCode::getCode, e -> e));

	public static ResponseCode findByCode(int code) {
		return Optional.ofNullable(map.get(code)).orElseThrow(() -> new McmpException(ResponseCode.UNKNOWN_ERROR));
	}
}
