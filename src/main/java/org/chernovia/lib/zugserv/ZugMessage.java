package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.enums.ZugScope;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public class ZugMessage implements JSONifier, Comparable<ZugMessage> {

    public static class ZugText implements JSONifier {
        List<Object> elements = new ArrayList<>();
        private int length = 0;

        public ZugText(JsonNode elNode) {
            // NEW: Validate structure
            try {
                InputValidator.validateZugText(elNode);
            } catch (IllegalArgumentException e) {
                ZugHandler.log(Level.WARNING, "Invalid ZugText: " + e.getMessage());
                // Fall back to treating as plain text
                elements.add(elNode.toString());
                return;
            }
            if (elNode instanceof ArrayNode elArray) {
                for (JsonNode el : elArray) {
                    if (el.has(ZugFields.TXT_EMOJI) && el.get(ZugFields.TXT_EMOJI).isInt()) {
                        elements.add(el.get(ZugFields.TXT_EMOJI).asInt());
                        length++;
                    } else if (el.has(ZugFields.TXT_ASCII) && el.get(ZugFields.TXT_ASCII).isTextual()) {
                        String text = el.get(ZugFields.TXT_ASCII).asText();
                        elements.add(text);
                        length += text.length();
                    } else if (el.isTextual()) { //fallback
                        elements.add(el.asText());
                        length += el.asText().length();
                    }
                }
            } else if (elNode.isTextual()) { //also fallback
                String text = elNode.asText();
                elements.add(text);
                length = text.length();
            }
        }

        int getLength() { //TODO: overflow?
            return length;
        }

        public ZugText(Object... elist) {
            Arrays.stream(elist).toList().forEach(el -> elements.add(el instanceof Integer i ? i : el.toString()));
        }

        @Override
        public ObjectNode toJSON2(Enum<?>... scopes) {
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
    public ObjectNode toJSON2(Enum<?>... scopes) {
        return msg.toJSON2(ZugScope.all).put(
                ZugFields.MSG_DATE, dateTime.toEpochSecond()).set(ZugFields.MSG_USER,author.toJSON2(ZugScope.basic));
    }
}
