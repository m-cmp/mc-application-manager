package kr.co.strato.http.connector;

public interface IConnector {
	
	/**
	 * 연결
	 */
	public Object connect();
	
	/**
	 * 연결 종료
	 */
	public boolean disconnect();
	
}
