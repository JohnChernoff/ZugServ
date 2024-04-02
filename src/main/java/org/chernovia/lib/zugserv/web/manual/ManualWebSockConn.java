package org.chernovia.lib.zugserv.web.manual;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.chernovia.lib.zugserv.web.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.*;

public class ManualWebSockConn extends ConnAdapter implements Runnable {
	public static final Logger logger = Logger.getLogger(WebSockConn.class.getName());
	public static final ObjectMapper mapper = new ObjectMapper();
	private final Socket socket;
	private InetAddress address;
	InputStream in; BufferedReader reader;
	OutputStream out; Scanner scanner;
	ConnListener listener;
	boolean noshake = true;
	boolean running = false;
	
	public ManualWebSockConn(Socket sock, ConnListener l) {
		socket = sock; listener = l;
		address = sock.getInetAddress();
		setID(getAddress().hashCode());
		try { 
			in = sock.getInputStream(); out = sock.getOutputStream(); 
			reader = new BufferedReader(new InputStreamReader(in));
		}
		catch (IOException argh) { argh.printStackTrace(); }
	}
	
	public boolean handshake() { if (noshake) return true;
		try {
			scanner = new Scanner(in, StandardCharsets.UTF_8);
			String data = scanner.useDelimiter("\\r\\n\\r\\n").next();
			Matcher get = Pattern.compile("^GET").matcher(data);
			if (get.find()) {
				Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
				match.find();
				byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
					+ "Connection: Upgrade\r\n"
					+ "Upgrade: websocket\r\n"
					+ "Sec-WebSocket-Accept: "
					+ Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((match.group(1) 
					+ "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8)))
					+ "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
				out.write(response, 0, response.length);
				return true;
			}
		}
		catch (Exception argh) { argh.printStackTrace(); }
		return false;
	}
	
	public String decode(byte[] data) throws IllegalArgumentException {
	    int datalength = data[1] & 127;
	    int indexFirstMask = 2;
	    if (datalength == 126) {
	        indexFirstMask = 4;
	    } else if (datalength == 127) {
	        indexFirstMask = 10;
	    }
	    byte[] masks = Arrays.copyOfRange(data, indexFirstMask, indexFirstMask + 4); 
	    int i = indexFirstMask + 4;
	    int index = 0;
	    StringBuilder output = new StringBuilder();
	    while (i < data.length) {
	        output.append((char) (data[i++] ^ masks[index++ % 4]));
	    }
	    return output.toString();
	}
	
	public byte[] encode(String mess) {
	    byte[] rawData = mess.getBytes();

	    int frameCount;
	    byte[] frame = new byte[10];

	    frame[0] = (byte) 129;

	    if (rawData.length <= 125) {
	        frame[1] = (byte) rawData.length;
	        frameCount = 2;
	    } else if (rawData.length >= 126 && rawData.length <= 65535) {
	        frame[1] = (byte) 126;
	        int len = rawData.length;
	        frame[2] = (byte)((len >> 8 ) & (byte)255);
	        frame[3] = (byte)(len & (byte)255); 
	        frameCount = 4;
	    } else {
	        frame[1] = (byte) 127;
	        int len = rawData.length;
	        frame[2] = (byte)((len >> 56 ) & (byte)255);
	        frame[3] = (byte)((len >> 48 ) & (byte)255);
	        frame[4] = (byte)((len >> 40 ) & (byte)255);
	        frame[5] = (byte)((len >> 32 ) & (byte)255);
	        frame[6] = (byte)((len >> 24 ) & (byte)255);
	        frame[7] = (byte)((len >> 16 ) & (byte)255);
	        frame[8] = (byte)((len >> 8 ) & (byte)255);
	        frame[9] = (byte)(len & (byte)255);
	        frameCount = 10;
	    }

	    int bLength = frameCount + rawData.length;

	    byte[] reply = new byte[bLength];

	    int bLim = 0;
	    for(int i=0; i<frameCount;i++){
	        reply[bLim] = frame[i];
	        bLim++;
	    }
		for (byte rawDatum : rawData) {
			reply[bLim] = rawDatum;
			bLim++;
		}
	    return reply;
	}
	
	@Override
	public void run() {
		running = true;
		if (handshake()) listener.connected(this);
		else {
			close("Bad Handshake");
		}
		while (running) {
			try {
				if (in.available() > 0)
					listener.newMsg(this, ZugServ.NO_CHAN, decode(in.readNBytes(in.available())));
			} catch (IOException | IllegalArgumentException e) {
				close(e.getMessage());
			}
		}
		listener.disconnected(this);
	}

	@Override
	public void close(String reason) {
		logger.log(Level.WARNING,"Closing socket: " + reason);
		try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
		running = false;
	}

	@Override
	public void setAddress(InetAddress a) {
		address = a;
	}

	@Override
	public InetAddress getAddress() { return address; }

	@Override
	public void tell(String type, String msg) {
		ObjectNode node = mapper.createObjectNode(); node.put("msg", msg); tell(type,node);			
	}

	@Override
	public void tell(String type, JsonNode msg) { //logger.log(Level.INFO,"Sending: " + msg);
		ObjectNode node = mapper.createObjectNode();
		node.put("type", type); node.set("data", msg);
		if (!socket.isClosed()) {
			try { out.write(encode(node.toString())); } 
			catch (IOException e) { close(e.getMessage()); }
		}
	}
}
