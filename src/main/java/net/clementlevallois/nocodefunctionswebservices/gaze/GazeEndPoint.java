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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.gaze.controller.CoocFunction;
import net.clementlevallois.gaze.controller.SimilarityFunction;
import net.clementlevallois.utils.Multiset;

/**
 *
 * @author LEVALLOIS
 */
public class GazeEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post("/api/gaze/cooc", (Context ctx) -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            Map<Integer, Multiset<String>> lines = new TreeMap();

            byte[] bodyAsBytes = ctx.bodyAsBytes();
            String body = new String(bodyAsBytes, StandardCharsets.UTF_8);
            if (body.isEmpty()) {
                objectBuilder.add("-99", "body of the request should not be empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                JsonReader jsonReader = Json.createReader(new StringReader(body));
                JsonObject jsonObject = jsonReader.readObject();
                for (String nextKey : jsonObject.keySet()) {
                    if (nextKey.equals("lines")) {
                        JsonObject linesJson = jsonObject.getJsonObject(nextKey);
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
                    }
                }

                CoocFunction coocFunction = new CoocFunction();
                String gexf = coocFunction.createGraphFromCooccurrences(lines, false);
                ctx.result(gexf.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
            }
        });

        app.post("/api/gaze/sim", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            Map<String, Set<String>> lines = new TreeMap();

            byte[] bodyAsBytes = ctx.bodyAsBytes();
            String body = new String(bodyAsBytes, StandardCharsets.UTF_8);
            if (body.isEmpty()) {
                objectBuilder.add("-99", "body of the request should not be empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                JsonReader jsonReader = Json.createReader(new StringReader(body));
                JsonObject jsonObject = jsonReader.readObject();
                for (String nextKey : jsonObject.keySet()) {
                    if (nextKey.equals("lines")) {
                        JsonObject linesJson = jsonObject.getJsonObject(nextKey);
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
                    }
                }

                SimilarityFunction simFunction = new SimilarityFunction();
                String gexf = simFunction.createSimilarityGraph(lines);
                ctx.result(gexf.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
            }
        });

        return app;

    }
}
