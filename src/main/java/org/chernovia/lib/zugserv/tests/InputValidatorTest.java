package org.chernovia.lib.zugserv.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chernovia.lib.zugserv.InputValidator;
import org.chernovia.lib.zugserv.enums.ZugAuthSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Test suite for InputValidator.
 *
 * <p>Run with: java InputValidatorTest
 *
 * <p>Tests are self-contained with assertion helpers.
 * No external dependencies required.
 */
public class InputValidatorTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static List<String> failureLog = new ArrayList<>();

    // ========================================================================
    // ASSERTION HELPERS
    // ========================================================================

    private static void assertTrue(String testName, boolean condition) {
        testsRun++;
        if (condition) {
            testsPassed++;
            System.out.println("✓ " + testName);
        } else {
            testsFailed++;
            String msg = "✗ " + testName + " - Expected true but got false";
            System.out.println(msg);
            failureLog.add(msg);
        }
    }

    private static void assertFalse(String testName, boolean condition) {
        testsRun++;
        if (!condition) {
            testsPassed++;
            System.out.println("✓ " + testName);
        } else {
            testsFailed++;
            String msg = "✗ " + testName + " - Expected false but got true";
            System.out.println(msg);
            failureLog.add(msg);
        }
    }

    private static void assertEquals(String testName, Object expected, Object actual) {
        testsRun++;
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            testsPassed++;
            System.out.println("✓ " + testName);
        } else {
            testsFailed++;
            String msg = "✗ " + testName + " - Expected '" + expected + "' but got '" + actual + "'";
            System.out.println(msg);
            failureLog.add(msg);
        }
    }

    private static void assertNotNull(String testName, Object value) {
        testsRun++;
        if (value != null) {
            testsPassed++;
            System.out.println("✓ " + testName);
        } else {
            testsFailed++;
            String msg = "✗ " + testName + " - Expected non-null value";
            System.out.println(msg);
            failureLog.add(msg);
        }
    }

    private static void assertNull(String testName, Object value) {
        testsRun++;
        if (value == null) {
            testsPassed++;
            System.out.println("✓ " + testName);
        } else {
            testsFailed++;
            String msg = "✗ " + testName + " - Expected null but got '" + value + "'";
            System.out.println(msg);
            failureLog.add(msg);
        }
    }

    private static void assertPresent(String testName, Optional<?> optional) {
        testsRun++;
        if (optional != null && optional.isPresent()) {
            testsPassed++;
            System.out.println("✓ " + testName);
        } else {
            testsFailed++;
            String msg = "✗ " + testName + " - Expected Optional.isPresent() but got " + optional;
            System.out.println(msg);
            failureLog.add(msg);
        }
    }

    private static void assertEmpty(String testName, Optional<?> optional) {
        testsRun++;
        if (optional != null && optional.isEmpty()) {
            testsPassed++;
            System.out.println("✓ " + testName);
        } else {
            testsFailed++;
            String msg = "✗ " + testName + " - Expected Optional.isEmpty() but got " + optional;
            System.out.println(msg);
            failureLog.add(msg);
        }
    }

    private static void assertThrows(String testName, String exceptionType, Runnable test) {
        testsRun++;
        try {
            test.run();
            testsFailed++;
            String msg = "✗ " + testName + " - Expected " + exceptionType + " but no exception was thrown";
            System.out.println(msg);
            failureLog.add(msg);
        } catch (Exception e) {
            if (e.getClass().getSimpleName().equals(exceptionType)) {
                testsPassed++;
                System.out.println("✓ " + testName);
            } else {
                testsFailed++;
                String msg = "✗ " + testName + " - Expected " + exceptionType + " but got " + e.getClass().getSimpleName();
                System.out.println(msg);
                failureLog.add(msg);
            }
        }
    }

    // ========================================================================
    // TEST METHODS - USERNAMES
    // ========================================================================

    private static void testValidUsernames() {
        System.out.println("\n=== Testing Valid Usernames ===");

        assertTrue("Valid username: simple", InputValidator.isValidUsername("valid_user"));
        assertTrue("Valid username: with dash", InputValidator.isValidUsername("user-123"));
        assertTrue("Valid username: mixed case with symbols", InputValidator.isValidUsername("User_Name-42"));
        assertTrue("Valid username: single char", InputValidator.isValidUsername("a"));
        assertTrue("Valid username: max length", InputValidator.isValidUsername("a".repeat(24)));
    }

    private static void testInvalidUsernames() {
        System.out.println("\n=== Testing Invalid Usernames ===");

        assertFalse("Invalid username: empty", InputValidator.isValidUsername(""));
        assertFalse("Invalid username: null", InputValidator.isValidUsername(null));
        assertFalse("Invalid username: with @", InputValidator.isValidUsername("user@domain"));
        assertFalse("Invalid username: with space", InputValidator.isValidUsername("user name"));
        assertFalse("Invalid username: with special char", InputValidator.isValidUsername("user!name"));
        assertFalse("Invalid username: too long", InputValidator.isValidUsername("a".repeat(25)));
        assertFalse("Invalid username: with period", InputValidator.isValidUsername("user.name"));
        assertFalse("Invalid username: only whitespace", InputValidator.isValidUsername("   "));
    }

    private static void testValidateUsername() {
        System.out.println("\n=== Testing Username Validation (with length limit) ===");

        assertEquals("Username validation: valid", "valid_user",
                InputValidator.validateUsername("valid_user", 24));
        assertEquals("Username validation: trimmed", "test_user",
                InputValidator.validateUsername("  test_user  ", 24));

        assertThrows("Username validation: empty throws", "IllegalArgumentException",
                () -> InputValidator.validateUsername("", 24));
        assertThrows("Username validation: null throws", "IllegalArgumentException",
                () -> InputValidator.validateUsername(null, 24));
        assertThrows("Username validation: invalid chars throw", "IllegalArgumentException",
                () -> InputValidator.validateUsername("user@invalid", 24));
    }

    // ========================================================================
    // TEST METHODS - AREA TITLES
    // ========================================================================

    private static void testValidAreaTitles() {
        System.out.println("\n=== Testing Valid Area Titles ===");

        assertTrue("Valid area title: simple", InputValidator.isValidAreaTitle("Valid Area"));
        assertTrue("Valid area title: single char", InputValidator.isValidAreaTitle("A"));
        assertTrue("Valid area title: with numbers", InputValidator.isValidAreaTitle("Game 2024"));
        assertTrue("Valid area title: with symbols", InputValidator.isValidAreaTitle("Area-Name_123"));
    }

    private static void testInvalidAreaTitles() {
        System.out.println("\n=== Testing Invalid Area Titles ===");

        assertFalse("Invalid area title: empty", InputValidator.isValidAreaTitle(""));
        assertFalse("Invalid area title: null", InputValidator.isValidAreaTitle(null));
        assertFalse("Invalid area title: too long", InputValidator.isValidAreaTitle("a".repeat(256)));
        assertFalse("Invalid area title: only whitespace", InputValidator.isValidAreaTitle("   "));
    }

    private static void testAreaTitleSanitization() {
        System.out.println("\n=== Testing Area Title Sanitization ===");

        // Test control character removal
        String dirty1 = "Area\u0000Name";
        String clean1 = InputValidator.validateAreaTitle(dirty1);
        assertTrue("Sanitize: removes null char", !clean1.contains("\u0000") && clean1.contains("Area"));

        // Test newline removal
        String dirty2 = "Area\nName";
        String clean2 = InputValidator.validateAreaTitle(dirty2);
        assertTrue("Sanitize: removes newlines", !clean2.contains("\n") && clean2.contains("Area"));

        // Test whitespace normalization
        String dirty3 = "Area    Name   Extra";
        String clean3 = InputValidator.validateAreaTitle(dirty3);
        assertTrue("Sanitize: normalizes whitespace", clean3.contains("Area") && !clean3.contains("    "));

        assertThrows("Sanitize: only control chars throws", "IllegalArgumentException",
                () -> InputValidator.validateAreaTitle("\u0000\u0001\u0002"));
    }

    // ========================================================================
    // TEST METHODS - MESSAGE LENGTH
    // ========================================================================

    private static void testValidMessageLength() {
        System.out.println("\n=== Testing Valid Message Length ===");

        assertTrue("Valid message: empty", InputValidator.isValidMessageLength(""));
        assertTrue("Valid message: null", InputValidator.isValidMessageLength(null));
        assertTrue("Valid message: short", InputValidator.isValidMessageLength("hello"));
        assertTrue("Valid message: max length", InputValidator.isValidMessageLength("a".repeat(512)));
    }

    private static void testInvalidMessageLength() {
        System.out.println("\n=== Testing Invalid Message Length ===");

        assertFalse("Invalid message: too long", InputValidator.isValidMessageLength("a".repeat(513)));
        assertFalse("Invalid message: way too long", InputValidator.isValidMessageLength("a".repeat(10000)));
    }

    private static void testValidateMessageLength() {
        System.out.println("\n=== Testing Message Length Validation (throws) ===");

        assertThrows("Message length: too long throws", "IllegalArgumentException",
                () -> InputValidator.validateMessageLength("a".repeat(513)));
    }

    // ========================================================================
    // TEST METHODS - ZUG TEXT VALIDATION
    // ========================================================================

    private static void testValidZugText() throws Exception {
        System.out.println("\n=== Testing Valid ZugText ===");

        JsonNode validEmoji = mapper.readTree("[{\"txt_emoji\": 42}]");
        assertTrue("Valid ZugText: emoji array", InputValidator.isValidZugText(validEmoji));

        JsonNode validAscii = mapper.readTree("[{\"txt_ascii\": \"hello\"}]");
        assertTrue("Valid ZugText: ascii array", InputValidator.isValidZugText(validAscii));

        JsonNode mixedArray = mapper.readTree("[{\"txt_emoji\": 1}, {\"txt_ascii\": \"x\"}, {\"txt_emoji\": 99}]");
        assertTrue("Valid ZugText: mixed emoji and ascii", InputValidator.isValidZugText(mixedArray));

        JsonNode singleEmoji = mapper.readTree("[{\"txt_emoji\": 0}]");
        assertTrue("Valid ZugText: single emoji", InputValidator.isValidZugText(singleEmoji));

        JsonNode longAscii = mapper.readTree("[{\"txt_ascii\": \"" + "a".repeat(256) + "\"}]");
        assertTrue("Valid ZugText: max length ascii", InputValidator.isValidZugText(longAscii));
    }

    private static void testInvalidZugText() throws Exception {
        System.out.println("\n=== Testing Invalid ZugText ===");

        // Both fields
        JsonNode both = mapper.readTree("[{\"txt_emoji\": 42, \"txt_ascii\": \"x\"}]");
        assertFalse("Invalid ZugText: both fields", InputValidator.isValidZugText(both));

        // Neither field
        JsonNode neither = mapper.readTree("[{\"other_field\": 42}]");
        assertFalse("Invalid ZugText: neither field", InputValidator.isValidZugText(neither));

        // Wrong type - emoji as string
        JsonNode wrongEmojiType = mapper.readTree("[{\"txt_emoji\": \"not_int\"}]");
        assertFalse("Invalid ZugText: emoji wrong type", InputValidator.isValidZugText(wrongEmojiType));

        // Wrong type - ascii as number
        JsonNode wrongAsciiType = mapper.readTree("[{\"txt_ascii\": 123}]");
        assertFalse("Invalid ZugText: ascii wrong type", InputValidator.isValidZugText(wrongAsciiType));

        // Too many elements
        StringBuilder hugeArray = new StringBuilder("[");
        for (int i = 0; i < 1001; i++) {
            if (i > 0) hugeArray.append(",");
            hugeArray.append("{\"txt_emoji\": 1}");
        }
        hugeArray.append("]");
        JsonNode huge = mapper.readTree(hugeArray.toString());
        assertFalse("Invalid ZugText: too many elements", InputValidator.isValidZugText(huge));

        // Not an array
        JsonNode notArray = mapper.readTree("{\"txt_emoji\": 42}");
        assertFalse("Invalid ZugText: not array", InputValidator.isValidZugText(notArray));

        // Null
        assertFalse("Invalid ZugText: null", InputValidator.isValidZugText(null));

        // Ascii too long
        JsonNode longAscii = mapper.readTree("[{\"txt_ascii\": \"" + "a".repeat(257) + "\"}]");
        assertFalse("Invalid ZugText: ascii too long", InputValidator.isValidZugText(longAscii));
    }

    private static void testValidateZugText() throws Exception {
        System.out.println("\n=== Testing ZugText Validation (throws) ===");

        JsonNode valid = mapper.readTree("[{\"txt_emoji\": 42}]");
        // Valid should not throw - no exception type passed, so we just verify no exception
        try {
            InputValidator.validateZugText(valid);
            testsPassed++;
            System.out.println("✓ ZugText validation: valid does not throw");
            testsRun++;
        } catch (Exception e) {
            testsFailed++;
            String msg = "✗ ZugText validation: valid does not throw - Got exception: " + e.getClass().getSimpleName();
            System.out.println(msg);
            failureLog.add(msg);
            testsRun++;
        }

        JsonNode invalid = mapper.readTree("[{\"txt_emoji\": \"not_int\"}]");
        assertThrows("ZugText validation: invalid throws", "IllegalArgumentException",
                () -> InputValidator.validateZugText(invalid));
    }

    // ========================================================================
    // TEST METHODS - JSON FIELD VALIDATION
    // ========================================================================

    private static void testReadValidatedInt() throws Exception {
        System.out.println("\n=== Testing Integer Field Validation ===");

        JsonNode node = mapper.readTree("{\"value\": 50, \"other\": \"text\"}");

        assertEquals("Read int: in range", 50,
                InputValidator.readValidatedInt(node, "value", 0, 100));
        assertNull("Read int: out of bounds (too high)",
                InputValidator.readValidatedInt(node, "value", 0, 40));
        assertNull("Read int: out of bounds (too low)",
                InputValidator.readValidatedInt(node, "value", 60, 100));
        assertNull("Read int: missing field",
                InputValidator.readValidatedInt(node, "missing", 0, 100));
        assertNull("Read int: wrong type",
                InputValidator.readValidatedInt(node, "other", 0, 100));
        assertNull("Read int: null node",
                InputValidator.readValidatedInt(null, "value", 0, 100));

        // Boundary tests - note: value 50 is at min when min=50
        assertEquals("Read int: exactly at min", 50,
                InputValidator.readValidatedInt(node, "value", 50, 100));
        assertEquals("Read int: exactly at max", 50,
                InputValidator.readValidatedInt(node, "value", 0, 50));
    }

    private static void testReadValidatedDouble() throws Exception {
        System.out.println("\n=== Testing Double Field Validation ===");

        JsonNode node = mapper.readTree("{\"value\": 3.14, \"intValue\": 42, \"other\": \"text\"}");

        assertEquals("Read double: in range", 3.14,
                InputValidator.readValidatedDouble(node, "value", 0.0, 10.0));
        assertNull("Read double: out of bounds (too high)",
                InputValidator.readValidatedDouble(node, "value", 0.0, 2.0));
        assertNull("Read double: out of bounds (too low)",
                InputValidator.readValidatedDouble(node, "value", 5.0, 10.0));
        assertNull("Read double: missing field",
                InputValidator.readValidatedDouble(node, "missing", 0.0, 10.0));
        assertNull("Read double: wrong type",
                InputValidator.readValidatedDouble(node, "other", 0.0, 10.0));
        assertNull("Read double: null node",
                InputValidator.readValidatedDouble(null, "value", 0.0, 10.0));

        // Int should work for double
        assertEquals("Read double: accepts int field", 42.0,
                InputValidator.readValidatedDouble(node, "intValue", 0.0, 100.0));
    }

    private static void testReadValidatedText() throws Exception {
        System.out.println("\n=== Testing Text Field Validation ===");

        JsonNode node = mapper.readTree("{\"value\": \"hello\", \"empty\": \"\", \"number\": 123}");

        assertEquals("Read text: valid", "hello",
                InputValidator.readValidatedText(node, "value", 100));
        assertNull("Read text: exceeds max length",
                InputValidator.readValidatedText(node, "value", 3));
        assertNull("Read text: empty string",
                InputValidator.readValidatedText(node, "empty", 100));
        assertNull("Read text: missing field",
                InputValidator.readValidatedText(node, "missing", 100));
        assertNull("Read text: wrong type",
                InputValidator.readValidatedText(node, "number", 100));
        assertNull("Read text: null node",
                InputValidator.readValidatedText(null, "value", 100));
        // maxLength of 0 means no limit
        assertEquals("Read text: no max length limit",
                "hello",
                InputValidator.readValidatedText(node, "value", 0));
    }

    private static void testReadValidatedBool() throws Exception {
        System.out.println("\n=== Testing Boolean Field Validation ===");

        JsonNode node = mapper.readTree("{\"trueVal\": true, \"falseVal\": false, \"text\": \"yes\"}");

        assertEquals("Read bool: true", true,
                InputValidator.readValidatedBool(node, "trueVal"));
        assertEquals("Read bool: false", false,
                InputValidator.readValidatedBool(node, "falseVal"));
        assertNull("Read bool: missing field",
                InputValidator.readValidatedBool(node, "missing"));
        assertNull("Read bool: wrong type",
                InputValidator.readValidatedBool(node, "text"));
        assertNull("Read bool: null node",
                InputValidator.readValidatedBool(null, "trueVal"));
    }

    // ========================================================================
    // TEST METHODS - ENUM VALIDATION
    // ========================================================================

    private static void testEnumParsing() {
        System.out.println("\n=== Testing Enum Parsing ===");

        assertEquals("Parse enum: lowercase", ZugAuthSource.lichess,
                InputValidator.parseEnum("lichess", ZugAuthSource.class));
        assertEquals("Parse enum: uppercase", ZugAuthSource.google,
                InputValidator.parseEnum("GOOGLE", ZugAuthSource.class));
        assertEquals("Parse enum: mixed case", ZugAuthSource.none,
                InputValidator.parseEnum("NoNe", ZugAuthSource.class));

        assertNull("Parse enum: invalid value",
                InputValidator.parseEnum("invalid_source", ZugAuthSource.class));
        assertNull("Parse enum: empty string",
                InputValidator.parseEnum("", ZugAuthSource.class));
        assertNull("Parse enum: null string",
                InputValidator.parseEnum(null, ZugAuthSource.class));
    }

    private static void testWhitelistValidation() {
        System.out.println("\n=== Testing Whitelist Validation ===");

        List<String> allowed = new ArrayList<>();
        Collections.addAll(allowed, "red", "green", "blue");

        assertTrue("Whitelist: value in list",
                InputValidator.isInWhitelist("red", allowed));
        assertFalse("Whitelist: value not in list",
                InputValidator.isInWhitelist("yellow", allowed));
        assertFalse("Whitelist: null value",
                InputValidator.isInWhitelist(null, allowed));
    }

    // ========================================================================
    // TEST METHODS - RESPONSE VALIDATION
    // ========================================================================

    private static void testValidResponseType() {
        System.out.println("\n=== Testing Response Type Validation ===");

        assertTrue("Valid response type: simple",
                InputValidator.isValidResponseType("confirmation"));
        assertTrue("Valid response type: with underscore",
                InputValidator.isValidResponseType("user_response"));
        assertTrue("Valid response type: max length",
                InputValidator.isValidResponseType("a".repeat(64)));

        assertFalse("Invalid response type: null",
                InputValidator.isValidResponseType(null));
        assertFalse("Invalid response type: empty",
                InputValidator.isValidResponseType(""));
        assertFalse("Invalid response type: whitespace only",
                InputValidator.isValidResponseType("   "));
        assertFalse("Invalid response type: too long",
                InputValidator.isValidResponseType("a".repeat(65)));
    }

    private static void testValidResponseValue() {
        System.out.println("\n=== Testing Response Value Type Validation ===");

        assertTrue("Response value: null is always valid",
                InputValidator.isValidResponseValue(null, Boolean.class));
        assertTrue("Response value: Boolean matches",
                InputValidator.isValidResponseValue(true, Boolean.class));
        assertTrue("Response value: Integer matches",
                InputValidator.isValidResponseValue(42, Integer.class));
        assertTrue("Response value: Double matches",
                InputValidator.isValidResponseValue(3.14, Double.class));
        assertTrue("Response value: String matches",
                InputValidator.isValidResponseValue("hello", String.class));

        assertFalse("Response value: Boolean doesn't match Integer",
                InputValidator.isValidResponseValue(true, Integer.class));
        assertFalse("Response value: Integer doesn't match String",
                InputValidator.isValidResponseValue(42, String.class));
    }

    // ========================================================================
    // TEST METHODS - JSON PARSING (WITH OPTIONAL)
    // ========================================================================

    private static void testSafeParseJSON() {
        System.out.println("\n=== Testing Safe JSON Parsing ===");

        assertPresent("Parse JSON: valid object",
                InputValidator.safeParseJSON("{}"));
        assertPresent("Parse JSON: valid array",
                InputValidator.safeParseJSON("[]"));
        assertPresent("Parse JSON: valid data",
                InputValidator.safeParseJSON("{\"key\": \"value\"}"));

        assertEmpty("Parse JSON: invalid syntax",
                InputValidator.safeParseJSON("{invalid json"));
        assertEmpty("Parse JSON: empty string",
                InputValidator.safeParseJSON(""));
        assertEmpty("Parse JSON: null input",
                InputValidator.safeParseJSON(null));
        assertEmpty("Parse JSON: whitespace only",
                InputValidator.safeParseJSON("   "));
    }

    private static void testRequiredFields() throws Exception {
        System.out.println("\n=== Testing Required Fields Validation ===");

        JsonNode complete = mapper.readTree("{\"type\": \"msg\", \"data\": {}}");
        assertTrue("Required fields: all present",
                InputValidator.hasRequiredFields(complete, "type", "data"));

        JsonNode missing = mapper.readTree("{\"type\": \"msg\"}");
        assertFalse("Required fields: data missing",
                InputValidator.hasRequiredFields(missing, "type", "data"));

        JsonNode nullField = mapper.readTree("{\"type\": \"msg\", \"data\": null}");
        assertFalse("Required fields: data is null",
                InputValidator.hasRequiredFields(nullField, "type", "data"));

        JsonNode notObject = mapper.readTree("[1, 2, 3]");
        assertFalse("Required fields: not object",
                InputValidator.hasRequiredFields(notObject, "type", "data"));

        assertFalse("Required fields: null node",
                InputValidator.hasRequiredFields(null, "type", "data"));
    }

    // ========================================================================
    // TEST METHODS - IP ADDRESS VALIDATION
    // ========================================================================

    private static void testIPAddressValidation() {
        System.out.println("\n=== Testing IP Address Validation ===");

        assertTrue("Valid IP: localhost", InputValidator.isValidIPAddress("127.0.0.1"));
        assertTrue("Valid IP: private", InputValidator.isValidIPAddress("192.168.1.1"));
        assertTrue("Valid IP: public example", InputValidator.isValidIPAddress("8.8.8.8"));
        assertTrue("Valid IP: all zeros", InputValidator.isValidIPAddress("0.0.0.0"));
        assertTrue("Valid IP: all 255", InputValidator.isValidIPAddress("255.255.255.255"));

        assertFalse("Invalid IP: null", InputValidator.isValidIPAddress(null));
        assertFalse("Invalid IP: empty", InputValidator.isValidIPAddress(""));
        assertFalse("Invalid IP: too many octets", InputValidator.isValidIPAddress("192.168.1.1.1"));
        assertFalse("Invalid IP: too few octets", InputValidator.isValidIPAddress("192.168.1"));
        assertFalse("Invalid IP: non-numeric", InputValidator.isValidIPAddress("192.168.a.1"));
        assertFalse("Invalid IP: out of range", InputValidator.isValidIPAddress("256.256.256.256"));
        assertFalse("Invalid IP: text", InputValidator.isValidIPAddress("not an ip"));
    }

    // ========================================================================
    // MAIN & REPORTING
    // ========================================================================

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║     InputValidator Test Suite - Plain Java      ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        try {
            // Username tests
            testValidUsernames();
            testInvalidUsernames();
            testValidateUsername();

            // Area title tests
            testValidAreaTitles();
            testInvalidAreaTitles();
            testAreaTitleSanitization();

            // Message length tests
            testValidMessageLength();
            testInvalidMessageLength();
            testValidateMessageLength();

            // ZugText tests
            testValidZugText();
            testInvalidZugText();
            testValidateZugText();

            // JSON field validation tests
            testReadValidatedInt();
            testReadValidatedDouble();
            testReadValidatedText();
            testReadValidatedBool();

            // Enum tests
            testEnumParsing();
            testWhitelistValidation();

            // Response validation tests
            testValidResponseType();
            testValidResponseValue();

            // JSON parsing tests (updated for Optional)
            testSafeParseJSON();
            testRequiredFields();

            // IP address tests
            testIPAddressValidation();

        } catch (Exception e) {
            System.err.println("\n✗ FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
            testsFailed++;
        }

        // Print summary
        printSummary();
    }

    private static void printSummary() {
        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║                  TEST SUMMARY                   ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println("Total tests run:    " + testsRun);
        System.out.println("Tests passed:       " + testsPassed + " ✓");
        System.out.println("Tests failed:       " + testsFailed + " ✗");
        System.out.println("Pass rate:          " + (testsRun > 0 ?
                String.format("%.1f%%", (testsPassed * 100.0) / testsRun) : "N/A"));

        if (testsFailed > 0) {
            System.out.println("\n╔══════════════════════════════════════════════════╗");
            System.out.println("║              FAILURE DETAILS                    ║");
            System.out.println("╚══════════════════════════════════════════════════╝");
            for (String failure : failureLog) {
                System.out.println(failure);
            }
        }

        System.out.println();
        if (testsFailed == 0) {
            System.out.println("🎉 ALL TESTS PASSED!");
            System.exit(0);
        } else {
            System.out.println("❌ SOME TESTS FAILED");
            System.exit(1);
        }
    }
}
