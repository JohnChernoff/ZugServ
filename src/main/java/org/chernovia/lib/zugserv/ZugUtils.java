package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public class ZugUtils {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    public static ObjectNode newJSON() {
        return JSON_MAPPER.createObjectNode();
    }

    public static JsonNode nullNode() { return JSON_MAPPER.nullNode(); }

    public static ArrayNode newJSONArray() {
        return JSON_MAPPER.createArrayNode();
    }

    public static JsonNode readTree(String content) {
        try {
            return JSON_MAPPER.readTree(content);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<JSONifier> makeJSONifiers(List<Object> list) {
        return list.stream().map(item -> item instanceof JSONifier ? (JSONifier)item : null).toList();
    }
    public static ObjectNode makeNoodle(String name, List<JSONifier> list) { //Noodle = array of JSON nodes
        //List<JSONifier> items = list.stream().map(i -> i instanceof JSONifier ? (JSONifier)i : null).toList();
        ObjectNode node = JSON_MAPPER.createObjectNode();
        ArrayNode arrayNode = JSON_MAPPER.createArrayNode();
        for (JSONifier n : list) if (n != null) arrayNode.add(n.toJSON());
        node.set(name,arrayNode);
        return node;
    }

    public static ObjectNode joinNodes(ObjectNode... nodes) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        for (ObjectNode n : nodes) if (n != null) node.setAll(n);
        return node;
    }

    public static <E> Optional<E> getRandomElement (Collection<E> e) {
        return e.stream().skip((int) (e.size() * Math.random())).findFirst();
    }

    public static InetAddress getAddressFromString(String addressStr) {
        try {
            String[] byteStrs = addressStr.split("\\.");
            byte[] bytes = new byte[byteStrs.length];
            for (int i=0;i<byteStrs.length;i++) bytes[i] = Byte.parseByte(byteStrs[i]);
            return InetAddress.getByAddress(bytes);
        }
        catch (UnknownHostException augh) {
            return InetAddress.getLoopbackAddress();
        }
    }
}
