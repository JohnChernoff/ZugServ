package org.chernovia.lib.zugserv;

import org.chernovia.lib.zugserv.enums.ZugServMsgType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ResponseManager {

    ZugArea area;
    public record OccupantResponse(Optional<Object> response, Occupant occupant) {}
    public record BoolResponse (Optional<Boolean> response, Occupant occupant) {}
    public record IntResponse (Optional<Integer> response, Occupant occupant) {}
    public record DoubleResponse (Optional<Double> response, Occupant occupant) {}
    public record StringResponse (Optional<String> response, Occupant occupant) {}

    public static class ZugResponse {
        CompletableFuture<List<OccupantResponse>> futureResponse;
        Object cancelValue;
        public ZugResponse(CompletableFuture<List<OccupantResponse>> futureResponse, Object cancelValue) {
            this.futureResponse = futureResponse;
            this.cancelValue = cancelValue;
        }
    }

    private final Map<String, ZugResponse> responseCheckerMap = new HashMap<>();

    public ResponseManager(ZugArea area) {
        this.area = area;
    }

    public void checkResponse(String responseType) { //log("Checking response: " + responseType);
        ZugResponse response = responseCheckerMap.get(responseType);
        List<OccupantResponse> responseMap = area.getOccupants().stream()
                .filter(occupant -> !occupant.isBot())
                .map(occupant -> new OccupantResponse(occupant.getResponse(responseType),occupant)).toList();
        //TODO: how can occupantResponse.response() be null?!
        if (responseMap.stream().allMatch(occupantResponse -> occupantResponse.response().isPresent())) {
            area.spam(ZugServMsgType.completedResponse,ZugUtils.newJSON().put(ZugFields.RESPONSE_TYPE,responseType));
            response.futureResponse.complete(responseMap);
        }
        else if (responseMap.stream()
                .map(r -> r.response).filter(Optional::isPresent)
                .anyMatch(optVal -> optVal.get().equals(response.cancelValue))) {
            area.spam(ZugServMsgType.cancelledResponse,ZugUtils.newJSON().put(ZugFields.RESPONSE_TYPE,responseType));
            response.futureResponse.complete(responseMap);
        }
    }

    public CompletableFuture<List<OccupantResponse>> requestResponse(String responseType, int timeout) {
        return requestResponse(responseType,null,timeout);
    }
    public CompletableFuture<List<OccupantResponse>> requestResponse(String responseType, Object cancelValue, int timeout) {
        //ZugManager.log("Requesting response " + responseType + "," + timeout + "," + cancelValue);
        CompletableFuture<List<OccupantResponse>> future = new CompletableFuture<>();
        responseCheckerMap.put(responseType, new ZugResponse(future,cancelValue));
        area.getOccupants().forEach(occupant -> occupant.setResponse(responseType,null));
        area.spam(ZugServMsgType.reqResponse, ZugUtils.newJSON().put(ZugFields.RESPONSE_TYPE,responseType));
        return future.completeOnTimeout(
                area.getOccupants().stream().filter(occupant -> !occupant.isBot())
                        .map(occupant -> new OccupantResponse(occupant.getResponse(responseType),occupant))
                        .toList()
                ,timeout, TimeUnit.SECONDS);
    }

    public CompletableFuture<List<OccupantResponse>> requestResponse(String responseType, int timeout, Class<?> classFilter) {
        return requestResponse(responseType,null,timeout,classFilter);
    }
    public CompletableFuture<List<OccupantResponse>> requestResponse(String responseType, Object cancelValue, int timeout, Class<?> classFilter) {
        //ZugManager.log(Level.FINE,"Requesting response " + responseType + "," + timeout + "," + classFilter);
        return requestResponse(responseType,cancelValue,timeout).thenApplyAsync(response ->
                response.stream().map(occupantResponse ->
                        (occupantResponse.response.isEmpty() || !classFilter.isAssignableFrom(occupantResponse.response.get().getClass()))
                                ? new OccupantResponse(Optional.empty(), occupantResponse.occupant) : occupantResponse
                ).toList()
        );
    }

    public CompletableFuture<List<BoolResponse>> requestBoolResponse(String responseType, int timeout) {
        return requestBoolResponse(responseType,null,timeout);
    }
    public CompletableFuture<List<BoolResponse>> requestBoolResponse(String responseType, Object cancelValue, int timeout) { //log("Requesting boolean response ");
        return requestResponse(responseType,cancelValue,timeout,Boolean.class).thenApplyAsync(response -> { //log("Received boolean response");
            return response.stream().map(occupantResponse ->
                    new BoolResponse(Optional.ofNullable((Boolean)occupantResponse.response.orElse(null)),occupantResponse.occupant)).toList();
        });
    }

    public CompletableFuture<List<IntResponse>> requestIntResponse(String responseType, int timeout) {
        return requestIntResponse(responseType,null,timeout);
    }
    public CompletableFuture<List<IntResponse>> requestIntResponse(String responseType, Object cancelValue, int timeout) {
        return requestResponse(responseType,cancelValue,timeout,Integer.class).thenApplyAsync(response ->
                response.stream().map(occupantResponse ->
                        new IntResponse(Optional.ofNullable((Integer)occupantResponse.response.orElse(null)),occupantResponse.occupant)).toList());
    }

    public CompletableFuture<List<DoubleResponse>> requestDoubleResponse(String responseType, int timeout) {
        return requestDoubleResponse(responseType,null,timeout);
    }
    public CompletableFuture<List<DoubleResponse>> requestDoubleResponse(String responseType, Object cancelValue, int timeout) {
        return requestResponse(responseType,cancelValue,timeout,Double.class).thenApplyAsync(response ->
                response.stream().map(occupantResponse ->
                        new DoubleResponse(Optional.ofNullable((Double)occupantResponse.response.orElse(null)),occupantResponse.occupant)).toList());
    }

    public CompletableFuture<List<StringResponse>> requestStringResponse(String responseType, int timeout) {
        return requestStringResponse(responseType,null,timeout);
    }
    public CompletableFuture<List<StringResponse>> requestStringResponse(String responseType, Object cancelValue, int timeout) {
        return requestResponse(responseType,cancelValue,timeout,String.class).thenApplyAsync(response ->
                response.stream().map(occupantResponse ->
                        new StringResponse(Optional.ofNullable((String)occupantResponse.response.orElse(null)),occupantResponse.occupant)).toList());
    }

    public CompletableFuture<Boolean> getConfirmation(String responseType, int timeout) { //log("Confirming...");
        return requestBoolResponse(responseType,false,timeout).thenApplyAsync(response -> { //log("Confirmation Response: " + response);
            return response.stream().allMatch(boolResponse -> boolResponse.response.orElse(false));
        });
    }
}
