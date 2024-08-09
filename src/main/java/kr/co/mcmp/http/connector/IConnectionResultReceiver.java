package kr.co.mcmp.http.connector;

public interface IConnectionResultReceiver {
	public static final int CONNECTION_OK = 1000;
	public static final int CONNECTION_FAIL = 1001;
	public static final int DISCONNECTION_OK = 1002;
	public static final int DISCONNECTION_FAIL = 1003;
	public static final int RESULT_OK = 1004;
	public static final int RESULT_FAIL = 1005;
	
	/**
	 * 커넥션 상태 또는 결과를 전송받는다.
	 * @param id : 상태 
	 * @param data : 데이터
	 */
	public void onReceiveResult(int id, Object data);
	
}
