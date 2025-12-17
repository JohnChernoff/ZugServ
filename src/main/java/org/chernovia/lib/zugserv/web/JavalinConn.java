package org.chernovia.lib.zugserv.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.websocket.WsConnectContext;
import org.chernovia.lib.zugserv.ConnAdapter;
import org.chernovia.lib.zugserv.ZugFields;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.framing.CloseFrame;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavalinConn extends ConnAdapter {
    private static final Logger logger = Logger.getLogger(JavalinConn.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    private final WsConnectContext ctx;
    private String address;

    public JavalinConn(WsConnectContext ctx) {
        this.ctx = ctx;
        String addr = ctx.session.getRemoteAddress().toString();
        setAddress(addr);

        // Generate a proper unique ID instead of using hashCode
        long uniqueId = System.nanoTime();  // Use timestamp for uniqueness
        setID(uniqueId);

        setStatus(Status.STATUS_CONNECTED);
    }

    public WsConnectContext getCtx() { return ctx; }

    @Override
    public void close(String reason) {
        close(CloseFrame.NORMAL,reason);
    }

    public void close(int code, String reason) {
        ctx.session.close(code,reason);
    }

    @Override
    public void setAddress(String a) {
        address = a;
    }

    @Override
    public String getAddress() { return address; }

    @Override
    public void tell(Enum<?> type, String msg) {
        ObjectNode node = mapper.createObjectNode();
        node.put(ZugFields.MSG, msg);
        tell(type,node);
    }

    @Override
    public void tell(Enum<?> type, JsonNode data) {
        ObjectNode node = mapper.createObjectNode();
        node.put(ZugFields.TYPE, type.name());
        node.set(ZugFields.DATA, data);
        try {
            if (ctx.session.isOpen()) {
                logger.log(Level.FINER, "SENDING: " + type.name() + " to " + getAddress());
                ctx.send(node.toString());
            }
            else logger.log(Level.WARNING,"Sending to closed session: " + getAddress() + " ,data: " + data.toString());
        }
        catch (WebsocketNotConnectedException argh) {
            logger.log(Level.WARNING,"Sending to unconnected session: " + getAddress() + " ,data: " + data.toString());
        } catch (Exception e) {
            logger.log(Level.WARNING,"Unknown error (" + e.getMessage() + ") at address: " + getAddress() + " ,data: " + data.toString());
        }
    }
}
