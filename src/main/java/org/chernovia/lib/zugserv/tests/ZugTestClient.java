package org.chernovia.lib.zugserv.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.net.URI;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple WebSocket test client for ZugServ debugging.
 * Connects, logs in as guest, and keeps connection open.
 */
public class ZugTestClient extends WebSocketClient {
    private static final Logger logger = Logger.getLogger("ZugTestClient");
    private static final ObjectMapper mapper = new ObjectMapper();
    private final CountDownLatch closeLatch = new CountDownLatch(1);
    private final String username = "TestBot_" + System.currentTimeMillis();

    public static void main(String[] args) {
        String serverUrl = args.length > 0 ? args[0] : "ws://127.0.0.1:2345/ws";
        logger.log(Level.INFO, "Connecting to: " + serverUrl);

        try {
            ZugTestClient client = new ZugTestClient(new URI(serverUrl));
            // Keep client alive
            client.keepAlive();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to connect: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public ZugTestClient(URI serverUri) {
        super(serverUri);
        connect();
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.log(Level.INFO, "✓ Connected to server");
        logger.log(Level.INFO, "Session ID: " + handshakedata.getFieldValue("session_id"));

        // Auto-login as guest
        loginAsGuest();
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode node = mapper.readTree(message);
            String type = node.get("type").asText();
            JsonNode data = node.get("data");

            logger.log(Level.INFO, "← Received: " + type);

            switch (type) {
                case "reqLogin":
                    logger.log(Level.INFO, "  Server requesting login");
                    break;

                case "logOK":
                    logger.log(Level.INFO, "  ✓ Login successful!");
                    logger.log(Level.INFO, "  User data: " + data.toString());
                    break;

                case "areaList":
                    logger.log(Level.INFO, "  Received area list");
                    logger.log(Level.INFO, "  Areas: " + data.get("areas").size());
                    break;

                case "updateServ":
                    logger.log(Level.INFO, "  Server update received");
                    break;

                default:
                    logger.log(Level.INFO, "  Data: " + data.toString());
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error parsing message: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.log(Level.WARNING, "✗ Connection closed: " + code + " - " + reason);
        closeLatch.countDown();
    }

    @Override
    public void onError(Exception ex) {
        logger.log(Level.SEVERE, "✗ WebSocket error: " + ex.getMessage());
        ex.printStackTrace();
        closeLatch.countDown();
    }

    public void send(ObjectNode msg) {
        try {
            String json = mapper.writeValueAsString(msg);
            logger.log(Level.INFO, "→ Sending: " + msg.get("type").asText());
            getConnection().send(json);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error sending message: " + e.getMessage());
        }
    }

    private void loginAsGuest() {
        ObjectNode loginMsg = mapper.createObjectNode();
        loginMsg.put("type", "login");

        ObjectNode data = mapper.createObjectNode();
        data.put("login_type", "none");  // Guest login
        data.put("name", username);

        loginMsg.set("data", data);
        send(loginMsg);
    }

    public void keepAlive() {
        logger.log(Level.INFO, "\n=== Test Client Ready ===");
        logger.log(Level.INFO, "Connected as: " + username);
        logger.log(Level.INFO, "Type 'quit' to exit, or press Enter to continue");
        logger.log(Level.INFO, "Connection will stay open for testing...\n");

        Scanner scanner = new Scanner(System.in);
        while (!getConnection().isClosed()) {
            String input = scanner.nextLine();
            if (input.equalsIgnoreCase("quit")) {
                logger.log(Level.INFO, "Closing connection...");
                getConnection().close();
                break;
            }
        }
        scanner.close();
    }
}

