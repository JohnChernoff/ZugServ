package org.chernovia.lib.zugserv.web.manual;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import org.chernovia.lib.zugserv.*;
import org.chernovia.lib.zugserv.web.*;

public class ManualWebSockServ implements ZugServ, Runnable {
	
	public static final Logger logger = Logger.getLogger(WebSockServ.class.getName());
	int port;
    public Vector<Connection> connections = new Vector<>();
    ServerSocket serverSocket; boolean running = false;
    ConnListener connListener;
    
    public ManualWebSockServ(int p, ConnListener l) {
    	port = p; connListener = l;
    }
    
	@Override
    public void run() {
		running = true;
        try { serverSocket = new ServerSocket(port); } 
        catch (IOException e) { logger.log(Level.SEVERE,"Error starting on port " + port); return; }
		if (SSLServ.logAddress(logger, port)) return;

		while(running) {
            try {
                Socket newSocket = serverSocket.accept();
                ManualWebSockConn conn = new ManualWebSockConn(newSocket,connListener);
                logger.log(Level.INFO,"Incoming Connection at address: " + conn.getAddress());
                connections.add(conn);
                new Thread(conn).start();
            } catch (IOException e) {
                logger.log(Level.WARNING,"IO EXCEPTION OCCURED WHEN LISTENING FOR CHAT CLIENTS!");
                e.printStackTrace();
            }
        }
    }
	
	@Override
	public ConnListener getConnListener() { return connListener; }

	@Override
	public Vector<Connection> getAllConnections(boolean active) {
		return connections;
	}

	@Override
	public void startSrv() {
		new Thread(this).start();
	}

	@Override
	public ServType getType() { return ZugServ.ServType.WEBSOCK_DEFAULT; }

	@Override
	public void broadcast(Enum<?> type, String msg, boolean active) {
	}

	@Override
	public void broadcast(Enum<?> type, JsonNode msg, boolean active) {
	}

	@Override
	public int getMaxConnections() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setMaxConnections(int c) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stopSrv() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isRunning() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setPause(boolean paused) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isPaused() {
		// TODO Auto-generated method stub
		return false;
	}

}
