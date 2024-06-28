package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * ZugUser encapsulates a Connection with indentiable data relating to an identifiable (typically logged in/authenticated) user.
 */
abstract public class ZugUser extends Timeoutable implements JSONifier {  // long lastMessage = Long.MAX_VALUE;
    private Connection conn;
    private UniqueName uniqueName;
    private boolean loggedIn;
    private String loginToken;

    /**
     * Unique Name is combination of a ZugUser's alphanumeric name/handle and their authentication source, if any.
     * @param name a username
     * @param source the authentication type (such as ZugFields.AuthSource.lichess)
     */
    public record UniqueName(String name, ZugFields.AuthSource source) {
        @Override
        public String toString() {
            return name + (source == ZugFields.AuthSource.none ? "" : ("@" + source.name()));
        }

        public ObjectNode toJSON() {
            return ZugUtils.newJSON().put(ZugFields.SOURCE,source.name()).put(ZugFields.NAME,name);
        }
    }

    //Set<ZugArea> areas = Collections.synchronizedSet(new LinkedHashSet<>());

    /**
     * Creates a ZugUser with a Connection, name, authentication source, and (possibly null) authentication token.
     * @param c a Connection
     * @param name an alphanumeric name
     * @param source an authentication source
     * @param token an (optional) authentication token
     */
    public ZugUser(Connection c, String name, ZugFields.AuthSource source, String token) {
        setConn(c); uniqueName = new UniqueName(name,source); loggedIn = true; loginToken = token;
    }

    /**
     * Gets the Connection associated with this user.
     * @return a Connection
     */
    public Connection getConn() { return conn; }

    /**
     * Sets the Connection associated with this user.
     * @param c a Connection
     */
    public void setConn(Connection c) {
        conn = c;
    }

    /**
     * Gets the authentication source this user logged in with (if any).
     * @return an authentication source (such as ZugFields.AuthSource.lichess)
     */
    public ZugFields.AuthSource getSource() { return uniqueName.source; }

    /**
     * Gets the user's (possibly null) login token.
     * @return the token (as a String)
     */
    public String getLoginToken() {
        return loginToken;
    }

    /**
     * Sets the user's login token.
     * @param loginToken the token (as a String)
     */
    public void setLoginToken(String loginToken) {
        this.loginToken = loginToken;
    }

    /**
     * Sets the user's UniqueName which consists of an alphanumeric name/handle and an authentication source (if any).
     * @param uniqueName the user's UniqueName
     */
    public void setUniqueName(UniqueName uniqueName) {
        this.uniqueName = uniqueName;
    }

    /**
     * Gets the user's UniqueName which consists of an alphanumeric name/handle and an authentication source (if any).
     * @return the user's UniqueName
     */
    public UniqueName getUniqueName() {
        return uniqueName;
    }

    /**
     * Gets the user's name/handle.
     * @return an alphanumeric name
     */
    public String getName() {
        return uniqueName.name;
    }


    public boolean isLoggedIn() { return loggedIn; }
    public void setLoggedIn(boolean b) { loggedIn = b; }


    /**
     * Indicates if the user used any authentication to log in.
     * @return false if unauthenticated
     */
    public boolean isGuest() {
        return getUniqueName().source().equals(ZugFields.AuthSource.none);
    }

    /**
     * Sends an alphanumeric message to a user with a default message type of ZugFields.ServMsgType.servMsg.
     * @param msg the alphanumeric message to send
     */
    public void tell(String msg) {
        tell(ZugFields.ServMsgType.servMsg,msg);
    }

    /**
     * Sends a blank message with an enumerated message type.
     * @param t the enumerated message type
     */
    public void tell(Enum<?> t) { tell(t,""); }

    /**
     * Sends the user an alphanumeric message and enumerated message type.
     * @param t the enumerated message type
     * @param msg the alphanumeric message
     */
    public void tell(Enum<?> t, String msg) {
        if (loggedIn && conn != null) conn.tell(t,msg);
    }

    /**
     * Sends the user a JSON-formatted message with a default message type of ZugFields.ServMsgType.servMsg.
     * @param json the JSON-formatted message
     */
    public void tell(JsonNode json) {
        if (loggedIn && conn != null) conn.tell(ZugFields.ServMsgType.servMsg,json);
    }

    /**
     * Sends the user a JSON-formatted message and enumerated message type.
     * @param t the enumerated message type
     * @param json the JSON-formatted message
     */
    public void tell(Enum<?> t, JsonNode json) {
        if (loggedIn && conn != null) conn.tell(t,json);
    }

    /**
     * Serializes the ZugUser (typically via toJSON()) to a Connection.
     * @param conn the Connection to update
     */
    public void update(Connection conn) {
        if (conn != null) conn.tell(ZugFields.ServMsgType.updateUser,toJSON());
    }

    public ObjectNode toJSON() {
        return ZugUtils.newJSON().put(ZugFields.LOGGED_IN,loggedIn).set(ZugFields.UNAME,uniqueName.toJSON());
    }
}
