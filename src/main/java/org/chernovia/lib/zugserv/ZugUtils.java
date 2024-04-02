package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public class ZugUtils {
    public static final ObjectMapper JSON_MAPPER = new ObjectMapper();
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
    @SafeVarargs
    public static ObjectNode makeTxtNode(Map.Entry<String,String>... fields) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        for (Map.Entry<String, String> pair : fields) node.put(pair.getKey(),pair.getValue());
        return node;
    }
    @SafeVarargs
    public static ObjectNode makeIntNode(Map.Entry<String,Integer>... fields) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        for (Map.Entry<String, Integer> pair : fields) node.put(pair.getKey(),pair.getValue());
        return node;
    }
    @SafeVarargs
    public static ObjectNode makeFloatNode(Map.Entry<String,Float>... fields) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        for (Map.Entry<String, Float> pair : fields) node.put(pair.getKey(),pair.getValue());
        return node;
    }
    @SafeVarargs
    public static ObjectNode makeDoubleNode(Map.Entry<String,Double>... fields) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        for (Map.Entry<String, Double> pair : fields) node.put(pair.getKey(),pair.getValue());
        return node;
    }
    @SafeVarargs
    public static ObjectNode makeBooleanNode(Map.Entry<String,Boolean>... fields) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        for (Map.Entry<String, Boolean> pair : fields) node.put(pair.getKey(),pair.getValue());
        return node;
    }
    @SafeVarargs
    public static ObjectNode makeJSONNode(Map.Entry<String, JsonNode>... fields) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        for (Map.Entry<String, JsonNode> pair : fields) node.set(pair.getKey(),pair.getValue());
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
