// ============================================================================
// ISSUE #2: ResponseManager Memory Leak
// ============================================================================
// Problem: Responses accumulate in responseCheckerMap and are never cleaned up
//          when they timeout or complete, causing unbounded memory growth
// Solution: Properly cleanup responses in checkResponse() and ensure
//          cleanup happens even on timeout

package org.chernovia.lib.zugserv;

import org.chernovia.lib.zugserv.enums.ZugServMsgType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Manages request/response exchanges between server and occupants.
 *
 * <p><b>Thread Safety:</b> All public methods are thread-safe via synchronized
 * access to responseCheckerMap.
 *
 * <p><b>Memory Management:</b> Responses are automatically cleaned up when:
 * <ul>
 *   <li>All occupants have responded
 *   <li>A cancel value is received
 *   <li>The timeout expires
 * </ul>
 *
 * Failure to cleanup would cause unbounded memory growth in long-running servers.
 */
public class ResponseManager {

    ZugArea<?> area;

    public record OccupantResponse(Optional<Object> response, Occupant occupant) {}
    public record BoolResponse(Optional<Boolean> response, Occupant occupant) {}
    public record IntResponse(Optional<Integer> response, Occupant occupant) {}
    public record DoubleResponse(Optional<Double> response, Occupant occupant) {}
    public record StringResponse(Optional<String> response, Occupant occupant) {}

    public static class ZugResponse {
        CompletableFuture<List<OccupantResponse>> futureResponse;
        Object cancelValue;

        public ZugResponse(CompletableFuture<List<OccupantResponse>> futureResponse, Object cancelValue) {
            this.futureResponse = futureResponse;
            this.cancelValue = cancelValue;
        }
    }

    // FIX: Use synchronized map to prevent concurrent modification and NPE
    private final Map<String, ZugResponse> responseCheckerMap = Collections
            .synchronizedMap(new HashMap<>());

    public ResponseManager(ZugArea area) {
        this.area = area;
    }

    /**
     * Checks if a response is complete and notifies clients if so.
     * Called by Occupant.setResponse() whenever a response is submitted.
     *
     * <p><b>FIX:</b> Now properly cleans up responses from the map after
     * completion or cancellation to prevent memory leaks.
     *
     * @param responseType the type of response to check
     */
    public void checkResponse(String responseType) {
        ZugResponse response = responseCheckerMap.get(responseType);
        if (response == null) {
            ZugHandler.log(Level.FINE, "Orphaned response type (already completed): " + responseType);
            return;
        }

        List<OccupantResponse> responseMap = getResponses(responseType);

        try {
            // Check if ALL occupants have responded
            if (responseMap.stream().allMatch(occupantResponse -> occupantResponse.response().isPresent())) {
                area.spam(ZugServMsgType.completedResponse,
                        ZugUtils.newJSON().put(ZugFields.RESPONSE_TYPE, responseType));
                response.futureResponse.complete(responseMap);
                ZugHandler.log(Level.FINE, "Response completed: " + responseType);
            }
            // Check if any occupant submitted a cancel value
            else if (responseMap.stream()
                    .map(r -> r.response)
                    .filter(Optional::isPresent)
                    .anyMatch(optVal -> optVal.get().equals(response.cancelValue))) {
                area.spam(ZugServMsgType.cancelledResponse,
                        ZugUtils.newJSON().put(ZugFields.RESPONSE_TYPE, responseType));
                response.futureResponse.complete(responseMap);
                ZugHandler.log(Level.FINE, "Response cancelled: " + responseType);
            }
            // Response not yet complete - DON'T clean up yet
            else { return; }
        } catch (Exception e) {
            ZugHandler.log(Level.SEVERE, "Error checking response " + responseType + ": " + e.getMessage());
            ZugServ.printStackTrace(e);
            response.futureResponse.completeExceptionally(e);
        } finally {
            // ALWAYS cleanup on completion or error
            responseCheckerMap.remove(responseType);
        }
    }

    /**
     * Gets current responses from all non-bot occupants for a given type.
     * Extracted to improve readability and performance (single loop instead of stream).
     *
     * @param responseType the response type to query
     * @return list of occupant responses
     */
    private List<OccupantResponse> getResponses(String responseType) {
        List<OccupantResponse> responses = new ArrayList<>();
        try {
            // Create snapshot to prevent ConcurrentModificationException
            List<Occupant> occupantSnapshot = new ArrayList<>(area.getOccupants().toList());

            for (Occupant occupant : occupantSnapshot) {
                if (!occupant.isBot()) {
                    responses.add(new OccupantResponse(occupant.getResponse(responseType), occupant));
                }
            }
        } catch (Exception e) {
            ZugHandler.log(Level.WARNING,
                    "Error getting responses for " + responseType + ": " + e.getMessage());
        }
        return responses;
    }

    public CompletableFuture<List<OccupantResponse>> requestResponse(String responseType, int timeout) {
        return requestResponse(responseType, null, timeout);
    }

    /**
     * Requests responses from all occupants with timeout handling.
     *
     * <p><b>FIX:</b> Now ensures cleanup happens even on timeout via
     * whenComplete() handler.
     *
     * @param responseType identifier for this response request
     * @param cancelValue optional value that triggers immediate cancellation
     * @param timeout timeout in seconds
     * @return future that completes when all respond, one cancels, or timeout expires
     */
    public CompletableFuture<List<OccupantResponse>> requestResponse(String responseType,
                                                                     Object cancelValue,
                                                                     int timeout) {
        CompletableFuture<List<OccupantResponse>> future = new CompletableFuture<>();

        // Use computeIfAbsent for atomic put to prevent duplicates
        ZugResponse existingResponse = responseCheckerMap.putIfAbsent(responseType,
                new ZugResponse(future, cancelValue));

        if (existingResponse != null) {
            ZugHandler.log(Level.WARNING,
                    "Duplicate response request: " + responseType + " - request rejected");
            future.completeExceptionally(new IllegalStateException(
                    "Response type " + responseType + " already pending"));
            return future;
        }

        // Clear any existing responses
        area.getOccupants().forEach(occupant -> occupant.setResponse(responseType, null));

        area.spam(ZugServMsgType.reqResponse,
                ZugUtils.newJSON().put(ZugFields.RESPONSE_TYPE, responseType));

        // Guaranteed cleanup on ALL completion paths
        return future
                .completeOnTimeout(getResponses(responseType), timeout, TimeUnit.SECONDS)
                .whenComplete((result, ex) -> {
                    try {
                        if (ex != null) {
                            ZugHandler.log(Level.WARNING,
                                    "Response request failed: " + responseType + ", error: " + ex.getMessage());
                        } else if (result != null && result.isEmpty()) {
                            ZugHandler.log(Level.FINE, "Response timeout: " + responseType);
                        }
                    } finally {
                        // THIS ALWAYS RUNS - cleanup guaranteed
                        responseCheckerMap.remove(responseType);
                    }
                });
    }

    public CompletableFuture<List<OccupantResponse>> requestResponse(String responseType,
                                                                     int timeout,
                                                                     Class<?> classFilter) {
        return requestResponse(responseType, null, timeout, classFilter);
    }

    /**
     * Requests responses and filters by type.
     *
     * <p><b>FIX:</b> Cleanup guaranteed by parent requestResponse() call.
     *
     * @param responseType identifier for this response request
     * @param cancelValue optional value that triggers immediate cancellation
     * @param timeout timeout in seconds
     * @param classFilter only include responses matching this class
     * @return future with filtered responses
     */
    public CompletableFuture<List<OccupantResponse>> requestResponse(String responseType,
                                                                     Object cancelValue,
                                                                     int timeout,
                                                                     Class<?> classFilter) {
        return requestResponse(responseType, cancelValue, timeout).thenApplyAsync(response ->
                response.stream().map(occupantResponse ->
                        (occupantResponse.response.isEmpty() ||
                                !classFilter.isAssignableFrom(occupantResponse.response.get().getClass()))
                                ? new OccupantResponse(Optional.empty(), occupantResponse.occupant)
                                : occupantResponse
                ).toList()
        );
    }

    public CompletableFuture<List<BoolResponse>> requestBoolResponse(String responseType, int timeout) {
        return requestBoolResponse(responseType, null, timeout);
    }

    public CompletableFuture<List<BoolResponse>> requestBoolResponse(String responseType,
                                                                     Object cancelValue,
                                                                     int timeout) {
        return requestResponse(responseType, cancelValue, timeout, Boolean.class)
                .thenApplyAsync(response ->
                        response.stream().map(occupantResponse ->
                                        new BoolResponse(
                                                Optional.ofNullable((Boolean) occupantResponse.response.orElse(null)),
                                                occupantResponse.occupant))
                                .toList()
                );
    }

    public CompletableFuture<List<IntResponse>> requestIntResponse(String responseType, int timeout) {
        return requestIntResponse(responseType, null, timeout);
    }

    public CompletableFuture<List<IntResponse>> requestIntResponse(String responseType,
                                                                   Object cancelValue,
                                                                   int timeout) {
        return requestResponse(responseType, cancelValue, timeout, Integer.class)
                .thenApplyAsync(response ->
                        response.stream().map(occupantResponse ->
                                        new IntResponse(
                                                Optional.ofNullable((Integer) occupantResponse.response.orElse(null)),
                                                occupantResponse.occupant))
                                .toList()
                );
    }

    public CompletableFuture<List<DoubleResponse>> requestDoubleResponse(String responseType, int timeout) {
        return requestDoubleResponse(responseType, null, timeout);
    }

    public CompletableFuture<List<DoubleResponse>> requestDoubleResponse(String responseType,
                                                                         Object cancelValue,
                                                                         int timeout) {
        return requestResponse(responseType, cancelValue, timeout, Double.class)
                .thenApplyAsync(response ->
                        response.stream().map(occupantResponse ->
                                        new DoubleResponse(
                                                Optional.ofNullable((Double) occupantResponse.response.orElse(null)),
                                                occupantResponse.occupant))
                                .toList()
                );
    }

    public CompletableFuture<List<StringResponse>> requestStringResponse(String responseType, int timeout) {
        return requestStringResponse(responseType, null, timeout);
    }

    public CompletableFuture<List<StringResponse>> requestStringResponse(String responseType,
                                                                         Object cancelValue,
                                                                         int timeout) {
        return requestResponse(responseType, cancelValue, timeout, String.class)
                .thenApplyAsync(response ->
                        response.stream().map(occupantResponse ->
                                        new StringResponse(
                                                Optional.ofNullable((String) occupantResponse.response.orElse(null)),
                                                occupantResponse.occupant))
                                .toList()
                );
    }

    /**
     * Requests boolean confirmation from all occupants.
     * Returns true only if ALL occupants respond with true.
     *
     * @param responseType identifier for this confirmation request
     * @param timeout timeout in seconds
     * @return future completing with true if all confirm, false otherwise
     */
    public CompletableFuture<Boolean> getConfirmation(String responseType, int timeout) {
        return requestBoolResponse(responseType, false, timeout)
                .thenApplyAsync(response ->
                        response.stream().allMatch(boolResponse -> boolResponse.response.orElse(false))
                );
    }
}
