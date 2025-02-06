package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

public class ZugOptions {
    public class Option implements JSONifier {

        public String description;
        public final String text;
        public final boolean boolVal;
        public final int intVal, intMin, intMax, intInc;
        public final double dblVal, dblMin, dblMax, dblInc;
        List<String> enums = new ArrayList<>();

        /**
         * Creates a new String Option.
         * @param txt the String text
         * @param desc the field description
         */
        public Option(String txt, String desc) {
            text = txt; boolVal = false; description = desc;
            intVal = intMin = intMax = intInc = Integer.MIN_VALUE;
            dblVal = dblMin = dblMax = dblInc = Double.MIN_VALUE;
        }

        /**
         * Creates a new String Option.
         * @param txt the String text
         * @param desc the field description
         * @param eList enumerated values
         */
        public Option(String txt, String desc, List<String> eList) {
            this(txt,desc);
            enums.addAll(eList);
        }

        /**
         * Creates a new boolean Option.
         * @param bool the boolean
         * @param desc the field description
         */
        public Option(boolean bool, String desc) {
            text = null; boolVal = bool; description = desc;
            intVal = intMin = intMax = intInc = Integer.MIN_VALUE;
            dblVal = dblMin = dblMax = dblInc = Double.MIN_VALUE;
        }

        /**
         * Creates a new integer Option.
         * @param i the integer value
         * @param min the minimum integer value
         * @param max the maximum integer value
         * @param inc the granularity allowed between the minimum and maximum values (e.g., 2 with min/max values of 0/10 would allow for 0,2,4,6,8,10)
         * @param desc the field description
         */
        public Option(int i, int min, int max, int inc, String desc) {
            text = null; boolVal = false; description = desc;
            intVal = i; intMin = min; intMax = max; intInc = inc;
            dblVal = dblMin = dblMax = dblInc = Double.MIN_VALUE;
        }

        /**
         * Creates a new double Option.
         * @param d the double value
         * @param min the minimum double value
         * @param max the maximum double value
         * @param inc the granularity allowed between the minimum and maximum values (e.g., .5 with min/max values of 0/2.5 would allow for 0,.5,1,1.5,2,2.5)
         * @param desc the field description
         */
        public Option(double d, double min, double max, double inc, String desc) {
            text = null; boolVal = false; description = desc;
            intVal = intMin = intMax = intInc = Integer.MIN_VALUE;
            dblVal = d; dblMin = min; dblMax = max; dblInc = inc;
        }

        @Override
        public ObjectNode toJSON() {
            if (text != null) return toJSON(text,null,null,null);
            else if (intVal != Integer.MIN_VALUE) return toJSON(intVal,intMin,intMax,intInc);
            else if (dblVal != Double.MIN_VALUE) return toJSON(dblVal,dblMin,dblMax,dblInc);
            else return toJSON(boolVal,null,null,null);
        }

        public ObjectNode toJSON(Object o, Number minVal, Number maxVal, Number incVal) {
            return toJSON(null,o,minVal,maxVal,incVal);
        }
        public ObjectNode toJSON(String field, Object o, Number minVal, Number maxVal, Number incVal) {
            ObjectNode node = ZugUtils.newJSON();
            if (o instanceof String str) {
                node.put(ZugFields.VAL,str);
            }
            else if (o instanceof Boolean bool) {
                node.put(ZugFields.VAL,bool);
            }
            else if (o instanceof Double d) { //ZugManager.log(field + " -> adding double: " + d);
                node.put(ZugFields.VAL,d);
                if (minVal instanceof Double minDbl) node.put(ZugFields.MIN,minDbl);
                else getDoubleTree(field,ZugFields.MIN).ifPresent(min -> node.put(ZugFields.MIN,min));
                if (maxVal instanceof Double maxDbl) node.put(ZugFields.MAX,maxDbl);
                else getDoubleTree(field,ZugFields.MAX).ifPresent(max -> node.put(ZugFields.MAX,max));
                if (incVal instanceof Double inc) node.put(ZugFields.INC,inc);
                else getDoubleTree(field,ZugFields.INC).ifPresent(inc -> node.put(ZugFields.INC,inc));
            }
            else if (o instanceof Integer i) { //ZugManager.log(field + " -> adding int: " + i);
                node.put(ZugFields.VAL,i);
                if (minVal instanceof Integer minInt) node.put(ZugFields.MIN,minInt);
                else getIntTree(field,ZugFields.MIN).ifPresent(min -> node.put(ZugFields.MIN,min));
                if (maxVal instanceof Integer maxInt) node.put(ZugFields.MAX,maxInt);
                else getIntTree(field,ZugFields.MAX).ifPresent(max -> node.put(ZugFields.MAX,max));
                if (incVal instanceof Integer inc) node.put(ZugFields.INC,inc);
                else getDoubleTree(field,ZugFields.INC).ifPresent(inc -> node.put(ZugFields.INC,inc));
            }
            ArrayNode arrayNode = ZugUtils.newJSONArray();
            enums.forEach(arrayNode::add);
            node.set(ZugFields.OPT_ENUM,arrayNode);
            return node.put(ZugFields.OPT_DESC,description);
        }

        /**
         * Sets an Option determined from an enumerated field and Object value.
         * @param field the enumerated field
         * @param o the value (either numeric, String, or boolean)
         * @return true if successful
         */
        public boolean setOption(Enum<?> field, Object o) { //options.set(field.name(),optionToJSON(field.name(),o,null,null,null));
            return setOption(field.name(),o);
        }

        /**
         * Sets an Option determined from an alphanumeric field and Object value.
         * @param field an alphanumeric field
         * @param o the value (either numeric, String, or boolean)
         * @return true if successful
         */
        public boolean setOption(String field, Object o) {
            if (o instanceof Number n) {
                JsonNode previousOption = options.get(field);
                if (previousOption != null) {
                    JsonNode previousValue = previousOption.get(ZugFields.VAL);
                    if (previousValue.isNumber()) {
                        JsonNode minNode = previousOption.get(ZugFields.MIN);
                        JsonNode maxNode = previousOption.get(ZugFields.MAX);
                        if (minNode != null && n.doubleValue() < minNode.asDouble()) return false;
                        else if (maxNode != null && n.doubleValue() > maxNode.asDouble()) return false;
                    }
                }
            }
            options.set(field, toJSON(field, o, null, null, null));
            return true;
        }
    }

    public ObjectNode options = ZugUtils.newJSON();
    private final Map<Enum<?>,Option> defaults = new HashMap<>();

    /**
     * Sets a default Option value.
     * @param field An anumerated field describing the Option
     * @param option an Option value
     */
    public void setDefault(Enum<?> field, Option option) {
        defaults.put(field,option);
    }

    public ZugOptions() {}

    @SafeVarargs
    public final void init(Map.Entry<Enum<?>,Option>... entries) {
        for (Map.Entry<Enum<?>, Option> entry : entries) {
            setDefault(entry.getKey(),entry.getValue());
        }
        for (Enum<?> field : defaults.keySet()) options.set(field.name(),defaults.get(field).toJSON());
    }

    /**
     * Get the integer value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @return the integer value if any, otherwise either the default value if any, or else zero and handleNoDefault() is called
     */
    public int getInt(Enum<?> field) {
        if (defaults.containsKey(field)) return getInt(field,defaults.get(field).intVal);
        else { handleNoDefault(field); return 0; }
    }

    /**
     * Get the integer value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @param def a default value
     * @return  the integer value if any, otherwise either the default value
     */
    public int getInt(Enum<?> field, int def) {
        return getOptInt(field.name()).orElse(def);
    }

    /**
     * Get the double value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @return the double value if any, otherwise either the default value if any, or else 0.0 and handleNoDefault() is called
     */
    public double getDbl(Enum<?> field) {
        if (defaults.containsKey(field)) return getInt(field,defaults.get(field).dblVal);
        else { handleNoDefault(field); return 0.0; }
    }

    /**
     * Get the double value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @param def a default value
     * @return the double value if any, otherwise either the default value
     */
    public double getInt(Enum<?> field, double def) {
        return getOptDbl(field.name()).orElse(def);
    }

    /**
     * Get the boolean value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @return the boolean value if any, otherwise either the default value if any, or else false and handleNoDefault() is called
     */
    public boolean getBool(Enum<?> field) {
        if (defaults.containsKey(field)) return getBool(field,defaults.get(field).boolVal);
        else { handleNoDefault(field); return false; }
    }

    /**
     * Get the boolean value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @param def a default value
     * @return the boolean value if any, otherwise either the default value
     */
    public boolean getBool(Enum<?> field, boolean def) {
        return getOptBool(field.name()).orElse(def);
    }

    /**
     * Get the String value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @return the String value if any, otherwise either the default value if any, or else "" and handleNoDefault() is called
     */
    public String getTxt(Enum<?> field) {
        if (defaults.containsKey(field)) return getInt(field,defaults.get(field).text);
        else { handleNoDefault(field); return ""; }
    }

    /**
     * Get the String value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @param def a default value
     * @return the String value if any, otherwise either the default value
     */
    public String getInt(Enum<?> field, String def) {
        return getOptTxt(field.name()).orElse(def);
    }

    //private convenience conversions
    private Optional<Integer> getOptInt(String field) { return getIntTree(field,ZugFields.VAL); }
    private Optional<Double> getOptDbl(String field) { return getDoubleTree(field,ZugFields.VAL); }
    private Optional<String> getOptTxt(String field) { return getStringTree(field,ZugFields.VAL); }
    private Optional<Boolean> getOptBool(String field) { return getBoolTree(field,ZugFields.VAL);}

    private Optional<JsonNode> getNodes(String... fields) {
        if (options == null) return Optional.empty();
        JsonNode node = options.deepCopy(); //TODO: what's going on here?
        for (String name : fields) {
            node = node.get(name); if (node == null) return Optional.empty();
        }
        return Optional.of(node);
    }

    public Optional<String> getStringTree(String... fields) {
        JsonNode node = getNodes(fields).orElse(null);
        if (node == null) return Optional.empty(); else return Optional.of(node.asText());
    }

    public Optional<Integer> getIntTree(String... fields) {
        JsonNode node = getNodes(fields).orElse(null);
        if (node == null) return Optional.empty(); else return Optional.of(node.asInt());
    }

    public Optional<Double> getDoubleTree(String... fields) {
        JsonNode node = getNodes(fields).orElse(null);
        if (node == null) return Optional.empty(); else return Optional.of(node.asDouble());
    }

    public Optional<Boolean> getBoolTree(String... fields) {
        JsonNode node = getNodes(fields).orElse(null);
        if (node == null) return Optional.empty(); else return Optional.of(node.asBoolean());
    }

    /**
     * Called upon attempted access of a nonexistent Option default value.
     * @param field the Option's descriptor field
     */
    void handleNoDefault(Enum<?> field) {
        new Error("No Default: " + field.name()).printStackTrace();
    }

    /**
     * Creates a new String Option entry.
     * @param e the Option enumeration
     * @param txt the String text
     * @param desc the field description
     */
    public Map.Entry<Enum<?>,Option> createOption(Enum<?> e, String txt, String desc) {
        return Map.entry(e,new Option(txt,desc));
    }

    /**
     * Creates a new String Option entry as part of a list of enumated values.
     * @param e the Option enumeration
     * @param txt the String text
     * @param desc the field description
     * @param elist enumerated values
     */
    public Map.Entry<Enum<?>,Option> createOption(Enum<?> e, String txt, String desc, List<String> elist) {
        return Map.entry(e,new Option(txt,desc,elist));
    }

    /**
     * Creates a new boolean Option entry.
     * @param e the Option enumeration
     * @param bool the boolean
     * @param desc the field description
     */
    public Map.Entry<Enum<?>,Option> createOption(Enum<?> e, boolean bool, String desc) {
        return Map.entry(e,new Option(bool,desc));
    }

    /**
     * Creates a new integer Option entry.
     * @param i the integer value
     * @param e the Option enumeration
     * @param min the minimum integer value
     * @param max the maximum integer value
     * @param inc the granularity allowed between the minimum and maximum values (e.g., 2 with min/max values of 0/10 would allow for 0,2,4,6,8,10)
     * @param desc the field description
     */
    public Map.Entry<Enum<?>,Option> createOption(Enum<?> e, int i, int min, int max, int inc, String desc) {
        return Map.entry(e,new Option(i,min,max,inc,desc));
    }

    /**
     * Creates a new double Option entry.
     * @param d the double value
     * @param e the Option enumeration
     * @param min the minimum double value
     * @param max the maximum double value
     * @param inc the granularity allowed between the minimum and maximum values (e.g., .5 with min/max values of 0/2.5 would allow for 0,.5,1,1.5,2,2.5)
     * @param desc the field description
     */
    public Map.Entry<Enum<?>,Option> createOption(Enum<?> e, double d, double min, double max, double inc, String desc) {
        return Map.entry(e,new Option(d,min,max,inc,desc));
    }

}
