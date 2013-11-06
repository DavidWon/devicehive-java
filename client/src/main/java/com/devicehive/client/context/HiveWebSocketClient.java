package com.devicehive.client.context;


import com.devicehive.client.json.GsonFactory;
import com.devicehive.client.json.strategies.JsonPolicyDef;
import com.devicehive.client.model.exceptions.HiveClientException;
import com.devicehive.client.model.exceptions.HiveServerException;
import com.devicehive.client.model.exceptions.InternalHiveClientException;
import com.devicehive.client.websocket.HiveClientEndpoint;
import com.devicehive.client.websocket.HiveWebsocketHandler;
import com.devicehive.client.websocket.SimpleWebsocketResponse;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import static javax.ws.rs.core.Response.Status.Family;

public class HiveWebSocketClient implements Closeable {

    private static final String REQUEST_ID_MEMBER = "requestId";
    private static final Integer POOL_SIZE = 100;
    private static final Long WAIT_TIMEOUT = 1L;
    private static final Integer AWAIT_TERMINATION_TIMEOUT = 10;
    private static Logger logger = LoggerFactory.getLogger(HiveWebSocketClient.class);
    private HiveClientEndpoint endpoint;
    private Map<String, SettableFuture<JsonObject>> websocketResponsesMap = new HashMap<>();
    private ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);

    public HiveWebSocketClient(URI socket, HiveContext hiveContext) {
        endpoint = new HiveClientEndpoint(socket);
        endpoint.addMessageHandler(new HiveWebsocketHandler(hiveContext, websocketResponsesMap));
    }

    public void sendMessage(JsonObject message) {
        endpoint.sendMessage(message.toString());
        String requestId = message.get(REQUEST_ID_MEMBER).getAsString();
        websocketResponsesMap.put(requestId, SettableFuture.<JsonObject>create());
        processResponse(requestId);
    }

    public <T> T sendMessage(JsonObject message, String responseMemberName, Type typeOfResponse,
                             JsonPolicyDef.Policy policy) {
        endpoint.sendMessage(message.toString());
        String requestId = message.get(REQUEST_ID_MEMBER).getAsString();
        websocketResponsesMap.put(requestId, SettableFuture.<JsonObject>create());
        return processResponse(requestId, responseMemberName, typeOfResponse, policy);
    }

    private void processResponse(final String requestId) {

        try {
            JsonObject result = websocketResponsesMap.get(requestId).get(WAIT_TIMEOUT, TimeUnit.MINUTES);

            if (result != null) {
                Gson gson = GsonFactory.createGson();
                SimpleWebsocketResponse response;
                try {
                    response = gson.fromJson(result, SimpleWebsocketResponse.class);
                } catch (JsonSyntaxException e) {
                    throw new InternalHiveClientException("Wrong type of response!");
                }
                if (response.getStatus().equals("success")) {
                    logger.debug("Request with id:" + requestId + "proceed successfully");
                    return;
                }
                Family errorFamily = Family.familyOf(response.getCode());
                switch (errorFamily) {
                    case SERVER_ERROR:
                        logger.warn(
                                "Request id: " + requestId + ". Error message:" + response.getError() + ". Status code:"
                                        + response.getCode());
                        throw new HiveServerException(response.getError(), response.getCode());
                    case CLIENT_ERROR:
                        logger.warn(
                                "Request id: " + requestId + ". Error message:" + response.getError() + ". Status code:"
                                        + response.getCode());
                        throw new HiveClientException(response.getError(), response.getCode());
                }
            }
        } catch (InterruptedException | TimeoutException e) {
            logger.warn("For request id: " + requestId + ". Error message: " + e.getMessage(), e);
        } catch (ExecutionException e) {
            logger.warn("For request id: " + requestId + ". Error message: " + e.getMessage(), e);
            throw new InternalHiveClientException(e.getMessage(), e);
        } finally {
            websocketResponsesMap.remove(requestId);
        }
    }

    private <T> T processResponse(final String requestId, final String responseMemberName, final Type typeOfResponse,
                                  final JsonPolicyDef.Policy receivePolicy) {
        try {
            JsonObject result = websocketResponsesMap.get(requestId).get(WAIT_TIMEOUT, TimeUnit.MINUTES);
            if (result != null) {
                if (result.get("status").getAsString().equals("success")) {
                    logger.debug("Request with id:" + requestId + "proceed successfully");
                } else {
                    Family errorFamily = Family.familyOf(result.get("code").getAsInt());
                    String error = result.get("error").getAsString();
                    Integer code = result.get("code").getAsInt();
                    switch (errorFamily) {
                        case SERVER_ERROR:
                            logger.warn("Request id: " + requestId + ". Error message:" + error + ". Status " +
                                    "code:" + code);
                            throw new HiveServerException(error, code);
                        case CLIENT_ERROR:
                            logger.warn("Request id: " + requestId + ". Error message:" + error + ". Status " +
                                    "code:" + code);
                            throw new HiveClientException(error, code);
                    }
                }
                Gson gson = GsonFactory.createGson(receivePolicy);
                T response;
                try {
                    response = gson.fromJson(result.get(responseMemberName), typeOfResponse);
                } catch (JsonSyntaxException e) {
                    throw new InternalHiveClientException("Wrong type of response!");
                }
                return response;
            }
        } catch (InterruptedException | TimeoutException e) {
            logger.warn("For request id: " + requestId + ". Error message: " + e.getMessage(), e);
        } catch (ExecutionException e) {
            logger.warn("For request id: " + requestId + ". Error message: " + e.getMessage(), e);
            throw new InternalHiveClientException(e.getMessage(), e);
        } finally {
            websocketResponsesMap.remove(requestId);
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(AWAIT_TERMINATION_TIMEOUT, TimeUnit.SECONDS))
                    logger.warn("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            logger.warn(ie.getMessage(), ie);
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
