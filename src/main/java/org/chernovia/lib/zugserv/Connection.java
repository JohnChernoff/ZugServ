package org.chernovia.lib.zugserv;

import java.net.InetAddress;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents a connected user.
 */
public interface Connection {

	/**
	 * Various possible Connection states.
	 */
	enum Status {
		STATUS_DISCONNECTED, STATUS_ERR, STATUS_OK, STATUS_LOGIN, STATUS_PASS, STATUS_CLOSING
	}

	/**
	 * Gets the server for this Connection.
	 * @return the server this Connection is currently connected to
	 */
	ZugServ getServ();

	/**
	 * Sets the server for this Connection.
	 * @param serv the server to associate with this Connection
	 */
	void setServ(ZugServ serv);

	/**
	 * Gets the Connection's ID number.
	 * @return a number (hopefully) uniquely indentifying this Connection
	 */
	long getID();

	/**
	 * Sets the Connection's ID number.
	 * @param id a number (hopefully) uniquely indentifying this Connection
	 */
	void setID(long id);

	/**
	 * Sets the Internet address associated with this Connection.
	 * @param address the (normally Internet-based) address to associate with this Connection
	 */
	void setAddress(String address);

	/**
	 * Gets an Internet address associated with this Connection.
	 * @return the Internet address associated with this Connection
	 */
	String getAddress();

	/**
	 * Indicates if a Connection is of the same origin as another.
	 * @param conn a Connection Object
	 * @return returns true if the two Connections share the same origin (typically an internet address)
	 */
	boolean isSameOrigin(Connection conn);

	/**
	 * Indicates if the Connection is automated.
	 * @return returns true if the Connection has been automated (i.e., its actions are now controlled by the server)
	 */
	boolean isAuto();

	/**
	 * Sets the automation of a Connection.
	 * @param a true to automate, false to un-automate
	 */
	void automate(boolean a);

	/**
	 * Indicates if the Connection is currently flooding/spamming the server.  Currently unimplemented.
	 * @param limit the acceptable amount of messages per a given timespan
	 * @param span the timespan in milliseconds
	 * @return true if flooding
	 */
	boolean isFlooding(int limit, long span);

	/**
	 * Closes a Connection.
	 * @param reason An arbitrarily verbose string explanation of why the Connection is to be closed
	 */
	void close(String reason);

	/**
	 * Sends an ASCII-based message to a Connection.
	 * @param type an enumeration of the category/type of message (such as "alert","password_request",and so forth)
	 * @param msg an alphanumeric message
	 */
	void tell(Enum<?> type, String msg);

	/**
	 * Sends JSON formatted data to a Connection.
	 * @param type an enumeration of the category/type of message (such as "update","error",etc.)
	 * @param msg the JSON data to be sent
	 */
	void tell(Enum<?> type, JsonNode msg); //TODO: make type an enum?

	/**
	 * Gets the status of a Connection.
	 * @return the current status of a Connection
	 */
	Status getStatus();

	/**
	 * Sets the status of a Connection.
	 * @param status the status to set a Connection to
	 */
	void setStatus(Status status);

	/**
	 * Gets the Connection's last recorded moment of activity.
	 * @return the time (in milliseconds) since the last (server defined) moment of "activity" by the Connection
	 */
	long lastPing();

	/**
	 * Records the Conenction's most recent moment of activity.
	 * @param t the time (in milliseconds) since the last (server defined) moment of "activity" by the Connection
	 */
	void setLastPing(long t);

	/**
	 * Gets the Connection's latency.
	 * @return the latency (lag) of a Connection expressed in milliseconds
	 */
	long getLatency();

	/**
	 * Sets the Connection's latency.
	 * @param t the latency (lag) of a Connection expressed in milliseconds
	 */
	void setLatency(long t);
}
