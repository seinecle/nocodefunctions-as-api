/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.gaze;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.utils.Multiset;

/**
 *
 * @author LEVALLOIS
 */
public class GazeEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post("/api/gaze/cooc", (Context ctx) -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            byte[] bodyAsBytes = ctx.bodyAsBytes();
            String body = new String(bodyAsBytes, StandardCharsets.UTF_8);
            if (body.isEmpty()) {
                String errorMsg = "body of the request should not be empty";
                ctx.result(errorMsg).status(HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            } else {
                RunnableGazeCooc gazeRunnable = new RunnableGazeCooc();
                JsonReader jsonReader = Json.createReader(new StringReader(body));
                JsonObject jsonObject = jsonReader.readObject();
                for (String nextKey : jsonObject.keySet()) {
                    if (nextKey.equals("lines")) {
                        JsonObject linesJson = jsonObject.getJsonObject(nextKey);
                        Map<Integer, Multiset<String>> lines = new HashMap();
                        for (String nextLineKey : linesJson.keySet()) {
                            JsonArray jsonArray = linesJson.getJsonArray(nextLineKey);
                            List<String> list = new ArrayList();
                            for (int i = 0; i < jsonArray.size(); i++) {
                                list.add(jsonArray.getString(i));
                            }
                            Multiset multiset = new Multiset();
                            multiset.addAllFromListOrSet(list);
                            lines.put(Integer.valueOf(nextLineKey), multiset);
                        }
                        gazeRunnable.setLines(lines);
                    }
                    if (nextKey.equals("sessionId")) {
                        String sessionId = jsonObject.getString(nextKey);
                        gazeRunnable.setSessionId(sessionId);
                    }
                    if (nextKey.equals("callbackURL")) {
                        String callbackURL = jsonObject.getString(nextKey);
                        gazeRunnable.setCallbackURL(callbackURL);
                    }
                    if (nextKey.equals("dataPersistenceId")) {
                        String dataPersistenceId = jsonObject.getString(nextKey);
                        gazeRunnable.setDataPersistenceId(dataPersistenceId);
                    }

                }
                gazeRunnable.runGazeCoocInBackgroundThread();
                ctx.result("ok").status(HttpURLConnection.HTTP_OK);
            }
        });

        app.post("/api/gaze/sim", ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            byte[] bodyAsBytes = ctx.bodyAsBytes();

            String body = new String(bodyAsBytes, StandardCharsets.UTF_8);
            if (body.isEmpty()) {
                String errorMsg = "body of the request should not be empty";
                ctx.result(errorMsg).status(HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            } else {
                RunnableGazeSim gazeSimRunnable = new RunnableGazeSim();
                JsonReader jsonReader = Json.createReader(new StringReader(body));
                JsonObject jsonObject = jsonReader.readObject();
                for (String nextKey : jsonObject.keySet()) {
                    if (nextKey.equals("lines")) {
                        JsonObject linesJson = jsonObject.getJsonObject(nextKey);
                        Map<String, Set<String>> lines = new TreeMap();
                        for (String nextLineKey : linesJson.keySet()) {
                            JsonArray jsonArray = linesJson.getJsonArray(nextLineKey);
                            List<String> list = new ArrayList();
                            for (int i = 0; i < jsonArray.size(); i++) {
                                list.add(jsonArray.getString(i));
                            }
                            Set<String> set = new HashSet();
                            set.addAll(list);
                            lines.put(nextLineKey, set);
                        }
                        gazeSimRunnable.setLines(lines);
                    }
                    if (nextKey.equals("sessionId")) {
                        String sessionId = jsonObject.getString(nextKey);
                        gazeSimRunnable.setSessionId(sessionId);
                    }
                    if (nextKey.equals("callbackURL")) {
                        String callbackURL = jsonObject.getString(nextKey);
                        gazeSimRunnable.setCallbackURL(callbackURL);
                    }
                    if (nextKey.equals("dataPersistenceId")) {
                        String dataPersistenceId = jsonObject.getString(nextKey);
                        gazeSimRunnable.setDataPersistenceId(dataPersistenceId);
                    }
                    if (nextKey.equals("parameters")) {
                        JsonObject parameters = jsonObject.getJsonObject(nextKey);
                        for (String nextKeyParam : parameters.keySet()) {
                            if (nextKeyParam.equals("minSharedTarget")) {
                                int minSharedTarget = parameters.getInt(nextKeyParam);
                                gazeSimRunnable.setMinSharedTarget(minSharedTarget);
                            }
                        }
                    }
                }
                gazeSimRunnable.runGazeSimInBackgroundThread();
                ctx.result("ok").status(HttpURLConnection.HTTP_OK);
            }
        });
        return app;
    }
}
