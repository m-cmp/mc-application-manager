package kr.co.mcmp.http.connector;

import java.util.ArrayList;
import java.util.List;


public abstract class AbstractConnector implements IConnector {
	private List<IConnectionResultReceiver> recivers;
	
	
	public List<IConnectionResultReceiver> getRecivers() {
		if(recivers == null) {
			recivers = new ArrayList<IConnectionResultReceiver>();
		}
		return recivers;
	}
	
	public void addConnectionResultReceiver(IConnectionResultReceiver reciver) {
		if(!getRecivers().contains(reciver)) {
			getRecivers().add(reciver);
		}
	}
	
	public void removeConnectionResultReceiver(IConnectionResultReceiver reciver) {
		if(getRecivers().contains(reciver)) {
			getRecivers().remove(reciver);
		}
	}


	
	
	public void onReceiveResult(int id) {
		onReceiveResult(id, null);
	}
	
	public void onReceiveResult(int id, Object data) {
		if(getRecivers() != null) {
			for(IConnectionResultReceiver receiver : getRecivers()) {
				receiver.onReceiveResult(id, data);
			}			
		}		
	}
	
}
