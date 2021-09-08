package org.chernovia.lib.zugserv.web;

//import java.util.logging.Logger;
import java.net.InetAddress;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.*;

public class WebSockConn extends ConnAdapter {
	//private static final Logger logger = Logger.getLogger(WebSockConn.class.getName());
	private static final ObjectMapper mapper = new ObjectMapper();
	private org.java_websocket.WebSocket socket;
	//private ConnListener listener;
	
	public WebSockConn(org.java_websocket.WebSocket sock, ConnListener l) {
		socket = sock; setID(getAddress().hashCode()); //listener = l; 
	}
	
	public org.java_websocket.WebSocket getSock() { return socket; }
	
	@Override
	public void close() { socket.close(); }

	@Override
	public InetAddress getAddress() { return socket.getRemoteSocketAddress().getAddress(); }

	@Override
	public void tell(String type, String msg) {
		ObjectNode node = mapper.createObjectNode(); node.put("msg", msg); tell(type,node);			
	}

	@Override
	public void tell(String type, JsonNode msg) { //logger.log(Level.INFO,"Sending: " + msg);
		ObjectNode node = mapper.createObjectNode();
		node.put("type", type); node.set("data", msg);
		if (!socket.isClosed()) {
			socket.send(node.toString());
		}
	}
}
