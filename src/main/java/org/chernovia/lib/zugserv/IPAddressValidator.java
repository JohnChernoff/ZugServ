// ============================================================================
// ADDRESS HANDLING IMPROVEMENTS
// ============================================================================
// 1. Server-side fallback: Capture remote address from connection
// 2. Client-side override: Allow client to report its own address
// 3. Validation: Ensure reported address is valid IP format
// 4. Immutability after login: Prevent mid-session address spoofing

package org.chernovia.lib.zugserv;

import java.util.regex.Pattern;

/**
 * Utility for IP address validation and normalization.
 *
 * <p>Validates IPv4 addresses in standard dotted-decimal notation.
 * Rejects invalid formats and private IP ranges that might indicate
 * client-side configuration issues.
 */
public class IPAddressValidator {

    // IPv4 pattern: matches x.x.x.x where x is 0-255
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
                    "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    // Private/reserved ranges
    private static final String[] PRIVATE_RANGES = {
            "127.",         // Localhost
            "10.",          // Private
            "172.16.",      // Private
            "192.168.",     // Private
            "169.254.",     // Link-local
            "0.",           // This network
            "255."          // Broadcast
    };

    /**
     * Validates an IP address string.
     *
     * @param ip the IP address to validate
     * @return true if valid public IPv4 address, false otherwise
     */
    public static boolean isValidPublicIP(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        // Check format
        if (!IPV4_PATTERN.matcher(ip).matches()) {
            return false;
        }

        // Reject private/reserved ranges
        for (String privateRange : PRIVATE_RANGES) {
            if (ip.startsWith(privateRange)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validates an IP address string (including private IPs).
     * Use this if you want to allow local/private networks.
     *
     * @param ip the IP address to validate
     * @return true if valid IPv4 address format, false otherwise
     */
    public static boolean isValidIP(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches();
    }
}
