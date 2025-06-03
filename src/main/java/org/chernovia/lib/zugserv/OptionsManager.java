package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.logging.Level;

public class OptionsManager implements JSONifier {

    public enum OptionType {opt_int,opt_dbl,opt_bool,opt_txt}
    public static class Option implements JSONifier  {

        public OptionType type;
        public String description, label;
        public String text;
        public boolean boolVal;
        int intVal, intMin, intMax, intInc;
        public double dblVal, dblMin, dblMax, dblInc;
        List<String> enums = new ArrayList<>();

        public Option(JsonNode node) {
            JsonNode valNode = node.get(ZugFields.OPT_VAL);
            JsonNode labNode = node.get(ZugFields.OPT_LABEL);
            JsonNode descNode = node.get(ZugFields.OPT_DESC);
            JsonNode maxNode = node.get(ZugFields.OPT_MAX);
            JsonNode minNode = node.get(ZugFields.OPT_MIN);
            JsonNode incNode = node.get(ZugFields.OPT_INC);

            String lab = labNode != null && labNode.isTextual() ? labNode.asText() : "";
            String desc = descNode != null && descNode.isTextual() ? descNode.asText() : "";

            if (valNode == null) {
                init(OptionType.opt_txt,lab,desc,
                        null,null,null,null,
                        null,null,null,null,null,null,null);
                optionError("No value found: " + node);
            }
            else {
                if (valNode.isTextual()) {
                    JsonNode enumNode = node.get(ZugFields.OPT_ENUM);
                    if (enumNode != null && enumNode.isArray()) {
                        List<String> elist = new ArrayList<>();
                        enumNode.elements().forEachRemaining(el -> {
                            if (el.isTextual()) elist.add(el.asText());
                        });
                        init(OptionType.opt_txt,lab,desc,
                                null,null,null,valNode.asText(),
                                null,null,null,null,null,null,elist);
                    }
                    else {
                        init(OptionType.opt_txt,lab,desc,
                                null,null,null,valNode.asText(),
                                null,null,null,null,null,null,null);
                    }
                }
                else if (valNode.isInt()) {
                    Integer min = minNode == null ? null : minNode.asInt(Integer.MIN_VALUE);
                    Integer max = maxNode == null ? null : maxNode.asInt(Integer.MAX_VALUE);
                    Integer inc = incNode == null ? null : incNode.asInt(1);
                    init(OptionType.opt_int,lab,desc,
                            null,valNode.asInt(),null,null,
                            min,max,inc,null,null,null,null);
                }
                else if (valNode.isDouble()) {
                    Double min = minNode == null ? null : minNode.asDouble(Double.MIN_VALUE);
                    Double max = maxNode == null ? null : maxNode.asDouble(Double.MAX_VALUE);
                    Double inc = incNode == null ? null : incNode.asDouble(1.0);
                    init(OptionType.opt_dbl,lab,desc,
                            null,null,valNode.asDouble(),null,
                            null,null,null,min,max,inc,null);
                }
                else if (valNode.isBoolean()) {
                    init(OptionType.opt_bool,lab,desc,
                            valNode.asBoolean(),null,null,null,
                            null,null,null,null,null,null,null);
                }
                else {
                    init(OptionType.opt_txt,lab,desc,
                            null,null,null,null,
                            null,null,null,null,null,null,null);
                    optionError("Unknown value: " + valNode);
                }
            }
        }

        private void init(OptionType type, String label, String description, Boolean boolVal, Integer intVal, Double dblVal, String text,
                          Integer intMin, Integer intMax, Integer intInc, Double dblMin, Double dblMax, Double dblInc, List<String> enums) {
            this.type = type;
            this.description = description != null ? description : "";
            this.label = label != null ? label : "";
            this.boolVal = boolVal != null ? boolVal : false;
            this.intVal = intVal != null ? intVal : Integer.MIN_VALUE;
            this.dblVal = dblVal != null ? dblVal : Double.MIN_VALUE;
            this.text = text != null ? text : "";
            this.intMin = intMin != null ? intMin : Integer.MIN_VALUE;
            this.intMax = intMax != null ? intMax : Integer.MAX_VALUE;
            this.intInc = intInc != null ? intInc : 1;
            this.dblMin = dblMin != null ? dblMin : Double.MIN_VALUE;
            this.dblMax = dblMax != null ? dblMax : Double.MAX_VALUE;
            this.dblInc = dblInc != null ? dblInc : 1;
            if (enums != null) this.enums = enums;
        }

        /**
         * Creates a new String Option.
         * @param txt the String text
         * @param lbl the field label
         * @param desc the field description
         */
        public Option(String txt, String lbl, String desc) {
            init(OptionType.opt_txt,lbl,desc,
                    null,null,null,txt,
                    null,null,null,null,null,null,null);
        }

        /**
         * Creates a new String Option.
         * @param txt the String text
         * @param lbl the field label
         * @param desc the field description
         * @param eList enumerated values
         */
        public Option(String txt, String lbl, String desc, List<String> eList) {
            init(OptionType.opt_txt,lbl,desc,
                    null,null,null,txt,
                    null,null,null,null,null,null,eList);
        }

        /**
         * Creates a new boolean Option.
         * @param bool the boolean
         * @param lbl the field label
         * @param desc the field description
         */
        public Option(boolean bool, String lbl, String desc) {
            init(OptionType.opt_bool,lbl,desc,
                    bool,null,null,null,
                    null,null,null,null,null,null,null);
        }

        /**
         * Creates a new integer Option.
         * @param i the integer value
         * @param min the minimum integer value
         * @param max the maximum integer value
         * @param inc the granularity allowed between the minimum and maximum values (e.g., 2 with min/max values of 0/10 would allow for 0,2,4,6,8,10)
         * @param lbl the field label
         * @param desc the field description
         */
        public Option(int i, int min, int max, int inc, String lbl, String desc) {
            init(OptionType.opt_int,lbl,desc,
                    null,i,null,null,
                    min,max,inc,null,null,null,null);
        }

        /**
         * Creates a new double Option.
         * @param d the double value
         * @param min the minimum double value
         * @param max the maximum double value
         * @param inc the granularity allowed between the minimum and maximum values (e.g., .5 with min/max values of 0/2.5 would allow for 0,.5,1,1.5,2,2.5)
         * @param lbl the field label
         * @param desc the field description
         */
        public Option(double d, double min, double max, double inc, String lbl, String desc) {
            init(OptionType.opt_dbl,lbl,desc,
                    null,null,d,null,
                    null,null,null,min,max,inc,null);
        }

        public boolean isNumeric() {
            return type == OptionType.opt_int || type == OptionType.opt_dbl;
        }

        public boolean setValue(Object o) {
            if (type == OptionType.opt_int && o instanceof Integer i && i >= intMin && i <= intMax) intVal = i;
            else if (type == OptionType.opt_dbl && o instanceof Double d && d >= dblMin && d <= dblMax) dblVal = d;
            else if (type == OptionType.opt_bool && o instanceof Boolean b) boolVal = b;
            else if (type == OptionType.opt_txt && o instanceof String s) text = s; //TODO: enums
            else return false;
            return true;
        }

        @Override
        public ObjectNode toJSON(List<String> scopes) { //ZugManager.log("Serializing: " + label);
            return switch (type) {
                case opt_int -> toJSON(intVal,intMin,intMax,intInc);
                case opt_dbl -> toJSON(dblVal,dblMin,dblMax,dblInc);
                case opt_bool -> toJSON(boolVal,null,null,null);
                case opt_txt -> toJSON(text,null,null,null);
            };
        }

        private ObjectNode toJSON(Object o, Number minVal, Number maxVal, Number incVal) {
            ObjectNode node = ZugUtils.newJSON();
            if (o instanceof String str) {
                node.put(ZugFields.OPT_VAL,str);
            }
            else if (o instanceof Boolean bool) {
                node.put(ZugFields.OPT_VAL,bool);
            }
            else if (o instanceof Double d) { //ZugManager.log(field + " -> adding double: " + d);
                node.put(ZugFields.OPT_VAL,d);
                if (minVal instanceof Double minDbl) node.put(ZugFields.OPT_MIN,minDbl);
                else optionError("MinVal not found for field: " + label);
                if (maxVal instanceof Double maxDbl) node.put(ZugFields.OPT_MAX,maxDbl);
                else optionError("MaxVal not found for field: " + label);
                if (incVal instanceof Double inc) node.put(ZugFields.OPT_INC,inc);
                else optionError("IncVal not found for field: " + label);
            }
            else if (o instanceof Integer i) { //ZugManager.log(field + " -> adding int: " + i);
                node.put(ZugFields.OPT_VAL,i);
                if (minVal instanceof Integer minInt) node.put(ZugFields.OPT_MIN,minInt);
                else optionError("MinVal not found for field: " + label);
                if (maxVal instanceof Integer maxInt) node.put(ZugFields.OPT_MAX,maxInt);
                else optionError("MaxVal not found for field: " + label);
                if (incVal instanceof Integer inc) node.put(ZugFields.OPT_INC,inc);
                else optionError("IncVal not found for field: " + label);
            }
            else optionError("Unknown data type for field: " + label);
            ArrayNode arrayNode = ZugUtils.newJSONArray();
            enums.forEach(arrayNode::add);
            node.set(ZugFields.OPT_ENUM,arrayNode);
            return node.put(ZugFields.OPT_DESC,description).put(ZugFields.OPT_LABEL,label);
        }

        private void optionError(String msg) {
            ZugManager.log(Level.WARNING,msg);
        }

    }

    private final Map<String,Option> optionsMap = new HashMap<>();

    /**
     * Sets an Option.
     * @param field An anumerated field describing the Option
     * @param option an Option value
     */
    public void setOption(Enum<?> field, Option option) {
        optionsMap.put(field.name(),option);
    }

    @SafeVarargs
    public OptionsManager(Map.Entry<Enum<?>,Option>... entries) {
        for (Map.Entry<Enum<?>, Option> entry : entries) { //ZugManager.log("Setting: " + entry.getKey() + " to " + entry.getValue());
            setOption(entry.getKey(),entry.getValue());
        }
    }

    public OptionsManager(JsonNode node) { //ZugManager.log("Setting: " + node.toString());
        node.fieldNames().forEachRemaining(field -> {
            optionsMap.put(field,new Option(node.get(field)));
        });
    }

    @Override
    public ObjectNode toJSON(List<String> scopes) {
        ObjectNode node = ZugUtils.newJSON();
        for (String field : optionsMap.keySet()) node.set(field,optionsMap.get(field).toJSON());
        return node;
    }

    /**
     * Sets an Option determined from an alphanumeric field and Object value.
     * @param field an alphanumeric field
     * @param o the value (either numeric, String, or boolean)
     * @return true if successful
     */
    public boolean setOption(Enum<?> field, Object o) {
        return optionsMap.get(field.name()).setValue(o);
    }

    /**
     * Get the integer value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @return the integer value if any, otherwise either the default value if any, or else zero and handleNoDefault() is called
     */
    public int getInt(Enum<?> field) {
        if (optionsMap.containsKey(field.name())) return optionsMap.get(field.name()).intVal;
        else { handleNoOption(field); return 0; }
    }

    public Optional<Integer> getOptInt(Enum<?> field) {
        if (optionsMap.containsKey(field.name())) return Optional.of(optionsMap.get(field.name()).intVal);
        else { handleNoOption(field); return Optional.empty(); }
    }

    /**
     * Get the double value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @return the double value if any, otherwise either the default value if any, or else 0.0 and handleNoDefault() is called
     */
    public double getDbl(Enum<?> field) {
        if (optionsMap.containsKey(field.name())) return optionsMap.get(field.name()).dblVal;
        else { handleNoOption(field); return 0.0; }
    }

    public Optional<Double> getOptDbl(Enum<?> field) {
        if (optionsMap.containsKey(field.name())) return Optional.of(optionsMap.get(field.name()).dblVal);
        else { handleNoOption(field); return Optional.empty(); }
    }

    /**
     * Get the boolean value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @return the boolean value if any, otherwise either the default value if any, or else false and handleNoDefault() is called
     */
    public boolean getBool(Enum<?> field) {
        if (optionsMap.containsKey(field.name())) return optionsMap.get(field.name()).boolVal;
        else { handleNoOption(field); return false; }
    }

    public Optional<Boolean> getOptBool(Enum<?> field) {
        if (optionsMap.containsKey(field.name())) return Optional.of(optionsMap.get(field.name()).boolVal);
        else { handleNoOption(field); return Optional.empty(); }
    }

    /**
     * Get the String value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @return the String value if any, otherwise either the default value if any, or else "" and handleNoDefault() is called
     */
    public String getTxt(Enum<?> field) {
        if (optionsMap.containsKey(field.name())) return optionsMap.get(field.name()).text;
        else { handleNoOption(field); return ""; }
    }

    public Optional<String> getOptTxt(Enum<?> field) {
        if (optionsMap.containsKey(field.name())) return Optional.of(optionsMap.get(field.name()).text);
        else { handleNoOption(field); return Optional.empty(); }
    }

    /**
     * Called upon attempted access of a nonexistent Option default value.
     * @param field the Option's descriptor field
     */
    void handleNoOption(Enum<?> field) {
        ZugManager.log(Level.WARNING,"Option not found: " + field.name());
    }

    /**
     * Creates a new String Option entry.
     * @param e the Option enumeration
     * @param txt the String text
     * @param desc the field description
     */
    public Map.Entry<Enum<?>,Option> createOption(Enum<?> e, String txt, String label,String desc) {
        return Map.entry(e, new Option(txt, label, desc));
    }

    /**
     * Creates a new String Option entry as part of a list of enumated values.
     * @param e the Option enumeration
     * @param txt the String text
     * @param desc the field description
     * @param elist enumerated values
     */
    public Map.Entry<Enum<?>,Option> createOption(Enum<?> e, String txt, String label, String desc, List<String> elist) {
        return Map.entry(e, new Option(txt, label, desc, elist));
    }

    /**
     * Creates a new boolean Option entry.
     * @param e the Option enumeration
     * @param bool the boolean
     * @param desc the field description
     */
    public Map.Entry<Enum<?>,Option> createOption(Enum<?> e, boolean bool, String label, String desc) {
        return Map.entry(e, new Option(bool, label, desc));
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
    public Map.Entry<Enum<?>,Option> createOption(Enum<?> e, int i, int min, int max, int inc, String label, String desc) {
        return Map.entry(e, new Option(i, min, max, inc, label, desc));
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
    public Map.Entry<Enum<?>,Option> createOption(Enum<?> e, double d, double min, double max, double inc, String label, String desc) {
        return Map.entry(e, new Option(d, min, max, inc, label, desc));
    }

}
