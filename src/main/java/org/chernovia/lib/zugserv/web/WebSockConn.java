package org.chernovia.lib.zugserv.web;

//import java.util.logging.Logger;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.*;

public class WebSockConn extends ConnAdapter {
	private static final Logger logger = Logger.getLogger(WebSockConn.class.getName());
	private static final ObjectMapper mapper = new ObjectMapper();
	private final org.java_websocket.WebSocket socket;
	//private ConnListener listener;
	
	public WebSockConn(org.java_websocket.WebSocket sock) { //, ConnListener l
		socket = sock; setID(getAddress().hashCode()); //listener = l; setHandle(Connection.UNKNOWN_HANDLE);
	}
	
	public org.java_websocket.WebSocket getSock() { return socket; }
	
	@Override
	public void close() { socket.close(); }

	@Override
	public InetAddress getAddress() { return socket.getRemoteSocketAddress().getAddress(); }

	@Override
	public void tell(String type, String msg) {
		ObjectNode node = mapper.createObjectNode(); node.put(ZugFields.MSG, msg); tell(type,node);
	}

	@Override
	public void tell(String type, JsonNode data) { //logger.log(Level.INFO,"Sending: " + data);
		ObjectNode node = mapper.createObjectNode();
		node.put(ZugFields.TYPE, type); node.set(ZugFields.DATA, data);
		if (!socket.isClosed()) {
			socket.send(node.toString());
		}
		else logger.log(Level.WARNING,"Sending to closed socket: " + getAddress() + " ,data: " + data.toString());
	}
}
