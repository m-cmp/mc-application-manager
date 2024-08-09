package kr.co.mcmp.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;



/**
 * Http 커넥터.
 * @author hclee
 *
 */
public class HttpUrlConnector {
	private String SERVER_URL;
	
	public static final int RESPONSE_CONNECT_ERROR = -1;
	public static final int RESPONSE_CONNECT_SUCCESS = 0;
	public static final int RESPONSE_PROGRESS_START = 1;
	public static final int RESPONSE_PROGRESS_STOP = 2;
	
	private HttpURLConnection conn;
	public static HttpUrlConnector thread;
	private OutputStreamWriter  bos;
	private InputStreamReader  bis;
	private StringBuffer command;
	private String method;
	private int timeout;
	private List<HttpUrlConnectListener> listener = new ArrayList<HttpUrlConnectListener>();
	
	public HttpUrlConnector(String url) {
		this(null, url, "GET", 5000);
	}
	
	public HttpUrlConnector(String url, String method, int timeout) {
		this(null, url, method, timeout);
	}
	
	public HttpUrlConnector(StringBuffer command, String url, String method, int timeout) {
		this.command = command;
		this.SERVER_URL = url;
		this.method = method;
		this.timeout = timeout;
	}

	public boolean connect() {
		boolean isConnect = false;
		try {	
			URL url = new URL(this.SERVER_URL);
			
			sendMessage(RESPONSE_PROGRESS_START, null);
			
			conn = (HttpURLConnection)url.openConnection();
			if(conn != null) {
				conn.setRequestMethod(method);
				conn.setConnectTimeout(timeout);
				conn.setReadTimeout(timeout);
				conn.setDoOutput(true);
				conn.setDoInput(true);
				conn.setUseCaches(false);
				conn.setDefaultUseCaches(false);
				conn.setAllowUserInteraction(true);
				conn.setRequestProperty("content-type", "application/x-www-form-urlencoded");
				
				
				bos = new OutputStreamWriter(conn.getOutputStream(), "utf-8");
				if(command != null) {
					sendCommand(command.toString().toCharArray());
				}				
				
				if(conn.getResponseCode() == HttpURLConnection.HTTP_OK ) {
					bis = new InputStreamReader(conn.getInputStream(), "utf-8");
					BufferedReader reader = new BufferedReader(bis);
					
					StringBuffer buffer = new StringBuffer();
					String str = null;
					while((str = reader.readLine())!= null) {
						buffer.append(str);
					}
					sendMessage(RESPONSE_CONNECT_SUCCESS, buffer.toString());
					isConnect = true;
				}				
			}
			
		} catch (IOException e) {
			sendMessage(RESPONSE_CONNECT_ERROR, null);
		} finally {
			sendMessage(RESPONSE_PROGRESS_STOP, null);
		}
		return isConnect;
	}
	
	public void sendMessage(int id, Object obj) {
		for(HttpUrlConnectListener l : listener) {
			l.onHttpResponse(id, obj);
		}
	}
	
	/**
	 * 리스너 등록
	 * @param l : 수신받을 리스너
	 */
	public void addHttpUrlConnectListener(HttpUrlConnectListener l) {
		if(!listener.contains(l)) {
			listener.add(l);
		}
	}
	
	/**
	 * 연결 종료
	 */
	public void disconnect() {
		try {
			if(bos != null) {
				bos.close();
			}
			if(bis != null) {
				bis.close();
			}
			if(conn != null) {
				conn.disconnect();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void sendCommand(char[] command) {
		try {
			bos.write(command, 0, command.length);
			bos.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}	
	
	public interface HttpUrlConnectListener {
		public void onHttpResponse(int id, Object data);
	}
	
}
