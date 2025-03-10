package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;

public class MessageManager {
    private final int maxMessages = 99;
    private final List<JsonNode> messages = new ArrayList<>();

    public void addMessage(final JsonNode msgNode) {
        messages.add(msgNode);
        if (messages.size() > maxMessages) messages.remove(0);
    }

    public ArrayNode toJSONArray() {
        ArrayNode historyNode = ZugUtils.newJSONArray();
        messages.forEach(historyNode::add);
        return historyNode;
    }
}
