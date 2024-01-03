package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface JSONifier {
    ObjectNode toJSON();
}
