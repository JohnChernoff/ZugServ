package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.enums.ZugScope;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ZugMessage implements JSONifier, Comparable<ZugMessage> {

    public static class ZugText implements JSONifier {
        List<Object> elements = new ArrayList<>();

        public ZugText(JsonNode elNode) {
            if (elNode instanceof ArrayNode elArray) {
                elArray.forEach(el -> elements.add(el.isInt() ? el.asInt() : el.asText()));
            }
            else elements.add(elNode.asText());
        }

        public ZugText(Object... elist) {
            Arrays.stream(elist).toList().forEach(el -> elements.add(el instanceof Integer i ? i : el.toString()));
        }

        @Override
        public ObjectNode toJSON(List<String> scopes) {
            ArrayNode txtArray = ZugUtils.newJSONArray();
            for (Object el : elements) {
                if (el instanceof Integer emoCode) txtArray.add(ZugUtils.newJSON().put(ZugFields.TXT_EMOJI,emoCode));
                else txtArray.add(ZugUtils.newJSON().put(ZugFields.TXT_ASCII,el.toString()));
            }
            return ZugUtils.newJSON().set(ZugFields.ZUG_TEXT,txtArray);
        }
    }

    ZugText msg;
    ZonedDateTime dateTime;
    ZugUser author;
    public ZugMessage(String msg, ZugUser author) {
        this(new ZugText(msg), author);
    }

    public ZugMessage(ZugText zugTxt, ZugUser author) {
        this.msg = zugTxt;
        dateTime = ZonedDateTime.now();
        this.author = author;
    }

    @Override
    public int compareTo(ZugMessage o) {
        return dateTime.compareTo(o.dateTime);
    }

    @Override
    public ObjectNode toJSON(List<String> scopes) {
        return


                msg.toJSON().put(ZugFields.MSG_DATE, dateTime.toEpochSecond()).set(ZugFields.MSG_USER,author.toJSON(ZugScope.basic));
    }
}
