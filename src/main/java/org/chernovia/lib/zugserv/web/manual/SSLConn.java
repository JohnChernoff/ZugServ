package org.chernovia.lib.zugserv.web.manual;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLSocket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.*;
import org.chernovia.lib.zugserv.web.*;

public class SSLConn extends ConnAdapter implements Runnable {

	public static final Logger logger = Logger.getLogger(WebSockConn.class.getName());
	public static final ObjectMapper mapper = new ObjectMapper();
	SSLSocket socket;
	InputStream in; BufferedReader reader;
	OutputStream out; BufferedWriter writer;
	ConnListener listener;
	boolean running = false;
	
	public SSLConn(SSLSocket sock, ConnListener l) {
		socket = sock; listener = l;
		try { 
			in = sock.getInputStream(); out = sock.getOutputStream(); 
			reader = new BufferedReader(new InputStreamReader(in));
			writer = new BufferedWriter(new OutputStreamWriter(out));
		}
		catch (IOException argh) { argh.printStackTrace(); }
	}
	
	@Override
	public InetAddress getAddress() {
		return socket.getInetAddress();
	}

	@Override
	public void close() {
		try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
		running = false;
	}

	@Override
	public void tell(String type, String msg) {
		ObjectNode node = mapper.createObjectNode(); node.put("msg", msg); tell(type,node);	
	}

	@Override
	public void tell(String type, JsonNode msg) {
		ObjectNode node = mapper.createObjectNode();
		node.put("type", type); node.set("data", msg);
		if (!socket.isClosed()) {
			try { writer.write(node.toString()); } 
			catch (IOException e) { logger.log(Level.INFO,e.getMessage()); close(); }
		}
	}

	@Override
	public void run() {
		while (running) {
			try {
				listener.newMsg(this, ZugServ.NO_CHAN, reader.readLine());
			} 
			catch (IOException e) { logger.log(Level.WARNING,e.getMessage()); close(); }
			catch (IllegalArgumentException e) { logger.log(Level.WARNING,e.getMessage()); close(); }
		}
		listener.disconnected(this);
	}

}
