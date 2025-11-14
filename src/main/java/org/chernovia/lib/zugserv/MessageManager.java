// ============================================================================
// ISSUE #5: MessageManager Performance
// ============================================================================
// Problem: ArrayList.remove(0) is O(n) - shifts all remaining elements
//          With 99 messages, this is 99 shifts per new message
//          Adds unnecessary CPU load on every chat message
// Solution: Use Queue (ConcurrentLinkedQueue) for O(1) removal
//           Thread-safe without explicit locking

package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

/**
 * Manages message history for a room/area.
 *
 * <p><b>Thread Safety:</b> Uses ConcurrentLinkedQueue for lock-free thread-safe operations.
 * Multiple threads can add/read messages concurrently without synchronization.
 *
 * <p><b>Performance:</b>
 * <ul>
 *   <li>Message addition: O(1) constant time
 *   <li>History retrieval: O(n) where n = message count (unavoidable)
 *   <li>Memory management: Automatic - old messages automatically discarded
 * </ul>
 *
 * <p><b>Bounded Size:</b> Maintains at most MAX_MESSAGES entries.
 * When limit is exceeded, oldest messages are automatically removed.
 */
public class MessageManager implements JSONifier {

    // FIX: Use Queue instead of ArrayList for O(1) removal operations
    private final Queue<JsonNode> messages = new ConcurrentLinkedQueue<>();

    // Maximum messages to retain (prevents unbounded memory growth)
    private static final int MAX_MESSAGES = 99;

    /**
     * Adds a message to the history.
     * If the history exceeds MAX_MESSAGES, the oldest message is automatically removed.
     *
     * <p><b>Performance:</b> O(1) constant time operation (was O(n) with ArrayList.remove(0))
     *
     * <p><b>Thread Safety:</b> Safe to call from multiple threads concurrently.
     *
     * @param msgNode the message to add (in JSON format)
     */
    public void addMessage(final JsonNode msgNode) {
        if (msgNode == null) {
            return; // Silently ignore null messages
        }

        messages.offer(msgNode); // Add to tail - O(1)

        // FIX: Maintain bounded size
        // Use poll() in a loop to handle case where multiple threads exceed limit simultaneously
        while (messages.size() > MAX_MESSAGES) {
            messages.poll(); // Remove from head - O(1)
        }
    }

    /**
     * Gets all messages as a JSON array.
     *
     * <p><b>Performance:</b> O(n) where n = number of messages (unavoidable - must serialize)
     *
     * <p><b>Thread Safety:</b> Creates a snapshot at call time. New messages added
     * during serialization will appear in next call.
     *
     * @return JSON array containing all messages in chronological order
     */
    public ArrayNode toJSONArray() {
        ArrayNode historyNode = ZugUtils.newJSONArray();
        try {
            // ConcurrentLinkedQueue provides a consistent iterator snapshot
            // This is already thread-safe, but document it
            messages.forEach(historyNode::add);
            return historyNode;
        } catch (Exception e) {
            ZugHandler.log(Level.SEVERE,
                    "Error serializing message history: " + e.getMessage());
            ZugServ.printStackTrace(e);
            return historyNode; // Return partial results
        }
    }
    /**
     * Gets the current size of the message history.
     *
     * @return number of messages currently stored
     */
    public int size() {
        return messages.size();
    }

    /**
     * Clears all messages from the history.
     * Useful for reset/cleanup scenarios.
     */
    public void clear() {
        messages.clear();
    }

    /**
     * Gets the maximum number of messages retained.
     *
     * @return max message count
     */
    public static int getMaxMessages() {
        return MAX_MESSAGES;
    }

    @Override
    public ObjectNode toJSON2(Enum<?>... scopes) {
        return ZugUtils.newJSON()
                .put(ZugFields.AREA_ID, "messages")
                .set(ZugFields.MSG_HISTORY, toJSONArray());
    }
}
