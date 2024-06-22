package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A JSONifier is an Object which can (partially or completely) serialize itself via its toJSON method
 */
@FunctionalInterface
public interface JSONifier {
    /**
     *
     * @return create a (partial or complete) JSON representation of the Class
     */
    ObjectNode toJSON();
}
