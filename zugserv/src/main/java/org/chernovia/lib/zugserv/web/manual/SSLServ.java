package org.chernovia.lib.zugserv.web.manual;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import javax.net.ssl.*;
import org.chernovia.lib.zugserv.web.WebSockServ;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.chernovia.lib.zugserv.*;
//import java.net.http.WebSocket;
//import org.chernovia.lib.zugserv.web.ZugServ.ServType;

public class SSLServ implements ZugServ, Runnable {
	public static final Logger logger = Logger.getLogger(WebSockServ.class.getName());
	int port = 5555;
    public ArrayList<Connection> connections = new ArrayList<>();
    SSLServerSocket serverSocket; boolean running = false;
    ConnListener connListener;
    
    public SSLServ(int p, ConnListener l) {
    	port = p; connListener = l;
    }
    
	@Override
    public void run() {
		final char[] password = "yourPassword".toCharArray();
		try {
			final KeyStore keyStore = KeyStore.getInstance(new File("yourKeystorePath.jks"), password);

			final TrustManagerFactory trustManagerFactory = 
					TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trustManagerFactory.init(keyStore);

			final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("NewSunX509");
			keyManagerFactory.init(keyStore, password);

			final SSLContext context = SSLContext.getInstance("SSL");//"SSL" "TLS"
			context.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);	
			final SSLServerSocketFactory factory = context.getServerSocketFactory();
			serverSocket = ((SSLServerSocket)factory.createServerSocket(port));
		}
        catch (IOException e) { logger.log(Level.SEVERE,"Error starting on port " + port); return; }
		catch (Exception fuck) { fuck.printStackTrace(); }
		
		running = true;

        try {
            String myaddr = InetAddress.getLocalHost().getHostAddress();
            logger.log(Level.INFO,"Server started at address [ " + myaddr + ":" + port + " ]");
        } catch (UnknownHostException e) { logger.log(Level.SEVERE,e.getMessage()); e.printStackTrace(); return; }

        while(running) {
            try {
                SSLSocket newSocket = (SSLSocket)serverSocket.accept(); //TODO: fix below
                logger.log(Level.INFO,"New socket creation at: " + newSocket.getInetAddress());
                //WebSockConn conn = new WebSockConn((WebSocket)newSocket,connListener);
                //connections.add(conn);
                //new Thread(conn).start();
            } catch (IOException e) {
                logger.log(Level.WARNING,"IO EXCEPTION OCCURED WHEN LISTENING FOR CHAT CLIENTS!");
                e.printStackTrace();
            }
        }
    }
	
	@Override
	public ConnListener getConnListener() { return connListener; }

	@Override
	public ArrayList<Connection> getAllConnections(boolean active) {
		return connections;
	}

	@Override
	public void broadcast(String type, String msg) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tch(int ch, String type, String msg) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startSrv() {
		new Thread(this).start();
	}

	@Override
	public ServType getType() { return ZugServ.ServType.TYPE_WEBSOCK; }

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
	public int getMaxChannels() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setMaxChannels(int c) {
		// TODO Auto-generated method stub
		
	}
}
