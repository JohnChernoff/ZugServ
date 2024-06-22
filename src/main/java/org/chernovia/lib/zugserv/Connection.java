package org.chernovia.lib.zugserv;

import java.net.InetAddress;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents a connected user.
 */
public interface Connection {
	enum Status {
		STATUS_DISCONNECTED, STATUS_ERR, STATUS_OK, STATUS_LOGIN, STATUS_PASS, STATUS_CLOSING
	}

	/**
	 *
	 * @return the server this Connection is currently connected to
	 */
	ZugServ getServ();

	/**
	 *
	 * @param serv the server to associate with this Connection
	 */
	void setServ(ZugServ serv);

	/**
	 *
	 * @return a number uniquely indentifying this Connection
	 */
	long getID();

	/**
	 *
	 * @param id a number uniquely indentifying this Connection
	 */
	void setID(long id);

	/**
	 *
	 * @param address the (normally internet-based) address to associate with this Connection
	 */
	void setAddress(InetAddress address);

	/**
	 *
	 * @return the (typically internet) address associated with this Connection
	 */
	InetAddress getAddress();

	/**
	 *
	 * @param conn a Connection Object
	 * @return returns true if the two Connections share the same origin (typically an internet address)
	 */
	boolean isSameOrigin(Connection conn);

	/**
	 *
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
	 * @return
	 */
	boolean isFlooding(int limit, long span);

	/**
	 *
	 * @param reason An arbitrarily verbode string explanation of why the Connection is to be closed
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
	 *
	 * @return the current status of a Connection
	 */
	Status getStatus();

	/**
	 *
	 * @param status the status to set a Connection to
	 */
	void setStatus(Status status);

	/**
	 *
	 * @return the time (in milliseconds) since the last (server defined) moment of "activity" by the Connection
	 */
	long lastPing();

	/**
	 *
	 * @param t the time (in milliseconds) since the last (server defined) moment of "activity" by the Connection
	 */
	void setLastPing(long t);

	/**
	 *
	 * @return the latency (lag) of a Connection expressed in milliseconds
	 */
	long getLatency();

	/**
	 *
	 * @param t the latency (lag) of a Connection expressed in milliseconds
	 */
	void setLatency(long t);
}
