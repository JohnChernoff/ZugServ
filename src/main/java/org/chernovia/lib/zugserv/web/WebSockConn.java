package org.chernovia.lib.zugserv.web;

//import java.util.logging.Logger;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.*;
import org.java_websocket.framing.CloseFrame;

public class WebSockConn extends ConnAdapter {
	private static final Logger logger = Logger.getLogger(WebSockConn.class.getName());
	private static final ObjectMapper mapper = new ObjectMapper();
	private final org.java_websocket.WebSocket socket;
	private InetAddress address;
	//private ConnListener listener;
	
	public WebSockConn(org.java_websocket.WebSocket sock) { //, ConnListener l
		socket = sock;
		setAddress(sock.getRemoteSocketAddress().getAddress());
		setID(getAddress().hashCode()); //listener = l; setHandle(Connection.UNKNOWN_HANDLE);
	}
	
	public org.java_websocket.WebSocket getSock() { return socket; }
	
	@Override
	public void close(String reason) { close(CloseFrame.NORMAL,reason); }

	public void close(int code, String reason) {
		socket.close(code,reason);
	}

	@Override
	public void setAddress(InetAddress a) {
		address = a;
	}

	@Override
	public InetAddress getAddress() { return address; }

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
