package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.enums.ZugAuthSource;
import org.chernovia.lib.zugserv.enums.ZugServMsgType;

import java.util.List;

/**
 * ZugUser encapsulates a Connection with indentiable data relating to an identifiable (typically logged in/authenticated) user.
 */
public class ZugUser extends Timeoutable implements JSONifier {  // long lastMessage = Long.MAX_VALUE;
    private Connection conn;
    private UniqueName uniqueName;
    private boolean loggedIn;
    private String loginToken = "";

    /**
     * Unique Name is combination of a ZugUser's alphanumeric name/handle and their authentication source, if any.
     */
    public static class UniqueName {
        public String name;
        public ZugAuthSource source;

        /**
         * Creates a UniqueName.
         *  @param n a username
         *  @param src the authentication type (such as ZugFields.AuthSource.lichess)
         */
        public UniqueName(String n, ZugAuthSource src) {
            name = n; source = src;
        }

        public UniqueName(JsonNode dataNode) {
            name = ZugManager.getTxtNode(dataNode,ZugFields.NAME).orElse("");
            try {
                source = ZugAuthSource.valueOf(ZugManager.getTxtNode(dataNode,ZugFields.SOURCE).orElse(ZugAuthSource.none.name()));
            }
            catch (IllegalArgumentException oops) {
                source = ZugAuthSource.none;
            }
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof UniqueName uniqueName && uniqueName.source.equals(source) && uniqueName.name.equals(name));
        }

        @Override
        public String toString() {
            return name + (source == ZugAuthSource.none ? "" : ("@" + source.name()));
        }

        public ObjectNode toJSON() {
            return ZugUtils.newJSON().put(ZugFields.SOURCE,source.name()).put(ZugFields.NAME,name);
        }
    }

    //Set<ZugArea> areas = Collections.synchronizedSet(new LinkedHashSet<>());

    /**
     * Creates a ZugUser with a Connection, name, authentication source, and (possibly null) authentication token.
     * @param c a Connection
     * @param uName a UniqueName
     */
    public ZugUser(Connection c, UniqueName uName) {
        setConn(c); uniqueName = uName; loggedIn = true;
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
    public ZugAuthSource getSource() { return uniqueName.source; }

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
        return getUniqueName().source.equals(ZugAuthSource.none);
    }

    /**
     * Sends an alphanumeric message to a user with a default message type of ZugFields.ServMsgType.servMsg.
     * @param msg the alphanumeric message to send
     */
    public void tell(String msg) {
        tell(ZugServMsgType.servMsg,msg);
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
        if (loggedIn && conn != null) conn.tell(ZugServMsgType.servMsg,json);
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
        if (conn != null) conn.tell(ZugServMsgType.updateUser,toJSON());
    }

    public boolean sameAddress(Connection conn) {
        return !conn.getAddress().isLoopbackAddress() && conn.getAddress().equals(getConn().getAddress());
    }

    public boolean sameUser(ZugUser.UniqueName name, Connection conn) {
        return name.source.equals(ZugAuthSource.none) ? sameAddress(conn) : name.equals(getUniqueName());
    }

    @Override
    public ObjectNode toJSON(List<String> scopes) {
        ObjectNode node = ZugUtils.newJSON();
        if (isBasic(scopes)) {
            node.put(ZugFields.LOGGED_IN,loggedIn).set(ZugFields.UNAME,uniqueName.toJSON());
        }
        return node;
    }

}
