package org.chernovia.lib.zugserv;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public abstract class ServAdapter implements ZugServ {

	private String newline = "\n";  
	private boolean generateLoginName = false;
	private boolean usePassword = false;
	private ConnListener listener;
	private ArrayList<Connection> conns = new ArrayList<>();
	private Logger logger = Logger.getLogger("ServLog");
	private int maxConn = 255, maxChan = 16;
	private ObjectMapper mapper = new ObjectMapper();
	
	public ServAdapter(ConnListener l) { listener = l; }
	
	public void connect(Connection conn) {
		log("Accepting connection #" + conns.size());
		conns.add(conn);
		if (generateLoginName) {
			conn.setHandle(generateLoginName()); 
			loggedIn(conn);
		}
		else {
			conn.setStatus(ConnAdapter.Status.STATUS_LOGIN);
			conn.tell(MSG_LOGIN,"Hello! Please enter your name.");
		}
	}
	
	public ConnListener getConnListener() { return listener; }
	
	public String generateLoginName() {
		return "Guest " + conns.size();
	}
	
	public void loggedIn(Connection conn) {
		conn.setStatus(ConnAdapter.Status.STATUS_OK);
		conn.tell(MSG_LOG_SUCCESS,conn.getHandle());
		listener.connected(conn);
	}

	public void disconnected(Connection conn) {
		log(conn.getHandle() + " disconnected.");
		conns.remove(conn);
		listener.disconnected(conn);
	}

	public ArrayList<Connection> getAllConnections(boolean active) { 
		if (active) {
			ArrayList<Connection> loggers = new ArrayList<>();
			for (Connection c : conns) if (c.getStatus() == Connection.Status.STATUS_OK) loggers.add(c);
			return loggers;
		}
		else return conns; 
	}
	
	private Connection getConnByHandle(String handle) {
		return getConnByHandle(handle,false);
	}
	private Connection getConnByHandle(String handle, boolean caseSensitive) {
		for (Connection conn: conns) {
			String h = conn.getHandle();
			if (h != null && ((!caseSensitive &&
			h.equalsIgnoreCase(handle)) ||
			h.equals(handle))) return conn;
		}
		return null;
	}
		
	public int getMaxConnections() { return maxConn; }
	public void setMaxConnections(int c) { maxConn = c; }

	public int getMaxChannels() { return maxChan; }
	public void setMaxChannels(int c) { maxChan = c; }
	
	//TODO: anti-spam?
	public void newMsg(Connection conn, int chan, String msg) {
		boolean handled = true;
		String[] tokens = msg.split(" ");
		String cmd = tokens[0];
		if (msg.equalsIgnoreCase("EXIT")) {
			conn.close();
		}
		else if (conn.getStatus() == ConnAdapter.Status.STATUS_LOGIN) { 
			login(conn,msg); 
		}
		else if (conn.getStatus() == ConnAdapter.Status.STATUS_PASS) {
			password(conn,msg);
		}
		else if (cmd.equalsIgnoreCase("TELL") && tokens.length > 2) {
			pTell(conn,getConnByHandle(tokens[1]),msg.substring(tokens[0].length() + 1 + tokens[1].length() + 1));
		}
		else if (cmd.equalsIgnoreCase("SHOUT") && tokens.length > 1) {
			shout(conn,msg.substring(tokens[0].length()+1));
		}
		else if (cmd.equalsIgnoreCase("WHO")) {
			String list = "Currently connected: " + newline;
			for (Connection c: conns) list += c.getHandle() + newline; 
			conn.tell("who", list);
		}
		else handled = false;
		if (!handled) listener.newMsg(conn, chan, msg);
	}
	
	public void broadcast(String type, String msg) {
		for (Connection conn: conns) conn.tell(type, msg);
	}
	
	public void tch(int chan, String type, String msg) {
		for (Connection conn: conns) {
			if (conn.getChannels().contains(chan)) conn.tell(type, msg);
		}
	}
		
	private void shout(Connection conn, String msg) {
		ObjectNode obj = mapper.createObjectNode();
		if (conn != null) obj.put("caster",conn.getHandle());
		obj.put("type",MSG_CAST);
		obj.put("msg",msg);
		for (Connection c: conns) c.tell(MSG_TXT,obj.toString());
	}
	
	//TODO: look up password
	private void password(Connection conn, String pwd) {
		if (isLegalPwd(pwd)) {
			loggedIn(conn);
		}
		else {
			conn.tell("bad_pass","Invalid password - try again!");
		}
	}
	
	private void login(Connection conn, String handle) {
		if (isLegalName(handle)) {
			conn.setHandle(handle);
			if (usePassword) {
				conn.setStatus(ConnAdapter.Status.STATUS_PASS);
				conn.tell(MSG_PASS,"Password?");
			}
			else loggedIn(conn);
		}
		else {
			conn.tell("bad_hand","Invalid handle - try again!");
		}
	}
	
	public boolean isLegalName(String n) {
		if (n==null || n.length()<2 || n.length()>32) return false;
		return (getConnByHandle(n) == null);
	}
	
	public boolean isLegalPwd(String pwd) {
		return (pwd==null || pwd.length()<2 || pwd.length()>16);
	}
		
	private void pTell(Connection sender, Connection receiver, String msg) {
		if (receiver == null) { sender.tell("no_hand","No such handle"); return; }
		ObjectNode obj = mapper.createObjectNode();
		obj.put("type", MSG_PRIV);
		obj.put("sender",sender.getHandle());
		obj.put("msg",msg);
		receiver.tell(MSG_TXT,obj.toString());
		sender.tell("ptell_ok","Told " + receiver.getHandle() + ".");
	}
	
	private void log(String msg) { log(msg,Level.INFO); }
	private void log(String msg, Level level) { logger.log(level,msg); }
	
}
