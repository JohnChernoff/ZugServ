package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collection;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Comprehensive input validation utility for ZugServ.
 *
 * <p><b>Design:</b> Centralized validation with clear error messages.
 * All methods return false/empty or throw IllegalArgumentException on invalid input.
 * Failures are logged for debugging without exposing implementation details to clients.
 *
 * <p><b>Usage:</b>
 * <pre>
 * // Validate before use
 * if (!InputValidator.isValidAreaTitle(title)) {
 *     throw new IllegalArgumentException("Invalid area title");
 * }
 *
 * // Or use with Optional
 * Optional<String> validTitle = InputValidator.validateAreaTitle(title);
 * </pre>
 */
public class InputValidator {

    // ========================================================================
    // NAME VALIDATION
    // ========================================================================

    private static final int MAX_NAME_LENGTH = 24;
    private static final int MIN_NAME_LENGTH = 1;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    /**
     * Validates a username.
     *
     * @param name the username to validate
     * @return true if valid (alphanumeric, dash, underscore only)
     */
    public static boolean isValidUsername(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            return false;
        }

        return NAME_PATTERN.matcher(name).matches();
    }

    /**
     * Validates and trims a username.
     *
     * @param name the username to validate
     * @param maxLength max allowed length
     * @return trimmed username if valid
     * @throws IllegalArgumentException if invalid
     */
    public static String validateUsername(String name, int maxLength) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        name = name.trim();

        if (name.length() > maxLength) {
            name = name.substring(0, maxLength);
        }

        if (!isValidUsername(name)) {
            throw new IllegalArgumentException(
                    "Username must be 1-" + maxLength + " chars, alphanumeric/dash/underscore only");
        }

        return name;
    }

    // ========================================================================
    // AREA/TITLE VALIDATION
    // ========================================================================

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MIN_TITLE_LENGTH = 1;

    /**
     * Validates an area/room title.
     *
     * @param title the title to validate
     * @return true if valid (non-empty, reasonable length)
     */
    public static boolean isValidAreaTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }

        int len = title.length();
        return len >= MIN_TITLE_LENGTH && len <= MAX_TITLE_LENGTH;
    }

    /**
     * Validates and sanitizes an area title.
     *
     * @param title the title to validate
     * @return sanitized title if valid
     * @throws IllegalArgumentException if invalid
     */
    public static String validateAreaTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Area title cannot be empty");
        }

        title = title.trim();

        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Area title exceeds " + MAX_TITLE_LENGTH + " characters");
        }

        // Remove control characters and excessive whitespace
        title = title.replaceAll("\\p{Cc}", "").replaceAll("\\s+", " ");

        if (title.isEmpty()) {
            throw new IllegalArgumentException("Area title contains only whitespace");
        }

        return title;
    }

    // ========================================================================
    // MESSAGE CONTENT VALIDATION
    // ========================================================================

    private static final int MAX_MESSAGE_LENGTH = 512;

    /**
     * Validates a message length.
     *
     * @param message the message to validate
     * @return true if within length limit
     */
    public static boolean isValidMessageLength(String message) {
        if (message == null) {
            return true; // Null is acceptable (empty message)
        }
        return message.length() <= MAX_MESSAGE_LENGTH;
    }

    /**
     * Validates message length and throws if exceeded.
     *
     * @param message the message to validate
     * @throws IllegalArgumentException if too long
     */
    public static void validateMessageLength(String message) {
        if (!isValidMessageLength(message)) {
            throw new IllegalArgumentException(
                    "Message exceeds " + MAX_MESSAGE_LENGTH + " characters");
        }
    }

    /**
     * Validates a ZugText JsonNode (emoji/text array).
     *
     * <p>Expected format: array of objects with either "txt_emoji" (int) or "txt_ascii" (string)
     *
     * @param textNode the ZugText node to validate
     * @return true if valid structure
     */
    public static boolean isValidZugText(JsonNode textNode) {
        if (textNode == null || !textNode.isArray()) {
            return false;
        }

        int maxElements = 1000; // Prevent DoS from huge emoji arrays
        if (textNode.size() > maxElements) {
            return false;
        }

        for (JsonNode element : textNode) {
            if (element == null || !element.isObject()) {
                return false;
            }

            // Must have EITHER emoji or ascii, not both, not neither
            boolean hasEmoji = element.has(ZugFields.TXT_EMOJI);
            boolean hasAscii = element.has(ZugFields.TXT_ASCII);

            if (!hasEmoji && !hasAscii) {
                return false; // Neither field present
            }

            if (hasEmoji && hasAscii) {
                return false; // Both fields present
            }

            if (hasEmoji && !element.get(ZugFields.TXT_EMOJI).isInt()) {
                return false; // Emoji must be int
            }

            if (hasAscii) {
                JsonNode ascii = element.get(ZugFields.TXT_ASCII);
                if (!ascii.isTextual() || ascii.asText().length() > 256) {
                    return false; // ASCII must be string, reasonable length
                }
            }
        }

        return true;
    }

    /**
     * Validates ZugText and throws on failure.
     *
     * @param textNode the node to validate
     * @throws IllegalArgumentException if invalid
     */
    public static void validateZugText(JsonNode textNode) {
        if (!isValidZugText(textNode)) {
            throw new IllegalArgumentException(
                    "Invalid ZugText format - expected array of {txt_emoji: int} or {txt_ascii: string}");
        }
    }

    // ========================================================================
    // JSON FIELD VALIDATION
    // ========================================================================

    /**
     * Safely reads a text field from JSON with validation.
     *
     * @param node the JSON object
     * @param fieldName the field name
     * @param maxLength maximum allowed length (0 = no limit)
     * @return the text value if present and valid, null otherwise
     */
    public static String readValidatedText(JsonNode node, String fieldName, int maxLength) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }

        JsonNode field = node.get(fieldName);
        if (!field.isTextual()) {
            return null;
        }

        String value = field.asText();

        if (value.isEmpty()) {
            return null;
        }

        if (maxLength > 0 && value.length() > maxLength) {
            return null; // Length exceeded
        }

        return value;
    }

    /**
     * Safely reads an integer field with bounds checking.
     *
     * @param node the JSON object
     * @param fieldName the field name
     * @param minValue minimum allowed value (inclusive)
     * @param maxValue maximum allowed value (inclusive)
     * @return the integer value if present and valid, null otherwise
     */
    public static Integer readValidatedInt(JsonNode node, String fieldName, int minValue, int maxValue) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }

        JsonNode field = node.get(fieldName);
        if (!field.isInt()) {
            return null;
        }

        int value = field.asInt();

        if (value < minValue || value > maxValue) {
            return null; // Out of bounds
        }

        return value;
    }

    /**
     * Safely reads a double field with bounds checking.
     *
     * @param node the JSON object
     * @param fieldName the field name
     * @param minValue minimum allowed value (inclusive)
     * @param maxValue maximum allowed value (inclusive)
     * @return the double value if present and valid, null otherwise
     */
    public static Double readValidatedDouble(JsonNode node, String fieldName, double minValue, double maxValue) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }

        JsonNode field = node.get(fieldName);
        if (!field.isDouble() && !field.isInt()) {
            return null;
        }

        double value = field.asDouble();

        if (value < minValue || value > maxValue) {
            return null; // Out of bounds
        }

        // Reject NaN and Infinity
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }

        return value;
    }

    /**
     * Safely reads a boolean field.
     *
     * @param node the JSON object
     * @param fieldName the field name
     * @return the boolean value if present and valid, null otherwise
     */
    public static Boolean readValidatedBool(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return null;
        }

        JsonNode field = node.get(fieldName);
        if (!field.isBoolean()) {
            return null;
        }

        return field.asBoolean();
    }

    // ========================================================================
    // ENUM VALIDATION
    // ========================================================================

    /**
     * Safely parses an enum value from text (case-insensitive).
     *
     * @param <T> the enum type
     * @param text the text value to parse
     * @param enumClass the enum class
     * @return the enum constant if valid, null if not found
     */
    public static <T extends Enum<T>> T parseEnum(String text, Class<T> enumClass) {
        if (text == null || text.isBlank()) {
            return null;
        }

        try {
            // Try to find matching enum constant (case-insensitive)
            String lowerText = text.toLowerCase();
            for (T constant : enumClass.getEnumConstants()) {
                if (constant.name().equals(lowerText)) {
                    return constant;
                }
            }
            // Not found
            ZugHandler.log(Level.FINE, "Invalid enum value: " + text + " for type " + enumClass.getSimpleName());
            return null;
        } catch (Exception e) {
            ZugHandler.log(Level.FINE, "Error parsing enum: " + e.getMessage());
            return null;
        }
    }

    /**
     * Validates that a value is in a whitelist.
     *
     * @param value the value to check
     * @param allowedValues the allowed values
     * @return true if value is in whitelist
     */
    public static <T> boolean isInWhitelist(T value, Collection<T> allowedValues) {
        return value != null && allowedValues.contains(value);
    }

    // ========================================================================
    // RESPONSE DATA VALIDATION
    // ========================================================================

    /**
     * Validates response type identifier.
     *
     * @param responseType the response type
     * @return true if valid
     */
    public static boolean isValidResponseType(String responseType) {
        return responseType != null && !responseType.isBlank() && responseType.length() <= 64;
    }

    /**
     * Validates a response value based on expected type.
     *
     * @param response the response value
     * @param expectedType the expected class (Boolean, Integer, Double, String)
     * @return true if response matches expected type
     */
    public static boolean isValidResponseValue(Object response, Class<?> expectedType) {
        if (response == null) {
            return true; // Null is acceptable (no response)
        }

        return expectedType.isAssignableFrom(response.getClass());
    }

    // ========================================================================
    // JSON PARSING HELPERS
    // ========================================================================

    /**
     * Validates that required fields are present in JSON object.
     *
     * @param node the JSON object
     * @param requiredFields field names that must be present
     * @return true if all fields present and non-null
     */
    public static boolean hasRequiredFields(JsonNode node, String... requiredFields) {
        if (node == null || !node.isObject()) {
            return false;
        }

        for (String field : requiredFields) {
            if (!node.has(field) || node.get(field).isNull()) {
                return false;
            }
        }

        return true;
    }

    // ========================================================================
    // ADDRESS VALIDATION
    // ========================================================================

    /**
     * Validates an IP address (with improved logic).
     * Allows private IPs including localhost.
     *
     * @param address the address to validate
     * @return true if valid IPv4 format
     */
    public static boolean isValidIPAddress(String address) {
        return address != null && IPAddressValidator.isValidIP(address);
    }

    // ========================================================================
    // LOGGING UTILITIES
    // ========================================================================

    /**
     * Logs a validation failure without exposing details to clients.
     *
     * @param validationType the type of validation that failed
     * @param inputPreview preview of input (sanitized for logging)
     */
    public static void logValidationFailure(String validationType, String inputPreview) {
        // Truncate preview to prevent log spam
        String preview = inputPreview != null ? inputPreview.substring(0, Math.min(100, inputPreview.length())) : "null";
        ZugHandler.log(Level.FINE, "Validation failed (" + validationType + "): " + preview);
    }
}
