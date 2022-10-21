/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.topics;

import net.clementlevallois.nocodefunctionswebservices.cowo.*;
import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.cowo.controller.CowoFunction;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.topics.topic.detection.function.controller.TopicDetectionFunction;
import net.clementlevallois.utils.Multiset;

/**
 *
 * @author LEVALLOIS
 */
public class TopicsEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post("/api/topics", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            TreeMap<Integer, String> lines = new TreeMap();
            Set<String> userSuppliedStopwords = new HashSet();
            String lang = "en";
            int precision = 5;
            boolean replaceStopwords = false;
            boolean isScientificCorpus = false;

            byte[] bodyAsBytes = ctx.bodyAsBytes();
            String body = new String(bodyAsBytes, StandardCharsets.US_ASCII);
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
                            lines.put(Integer.valueOf(nextLineKey), linesJson.getString(nextLineKey));
                        }
                    }
                    if (nextKey.equals("lang")) {
                        lang = jsonObject.getString(nextKey);
                    }
                    if (nextKey.equals("userSuppliedStopwords")) {
                        JsonObject linesJson = jsonObject.getJsonObject(nextKey);
                        for (String nextLineKey : linesJson.keySet()) {
                            userSuppliedStopwords.add(linesJson.getString(nextLineKey));
                        }
                    }
                    if (nextKey.equals("precision")) {
                        precision = jsonObject.getInt(nextKey);
                    }
                    if (nextKey.equals("replaceStopwords")) {
                        replaceStopwords = jsonObject.getBoolean(nextKey);
                    }
                    if (nextKey.equals("isScientificCorpus")) {
                        isScientificCorpus = jsonObject.getBoolean(nextKey);
                    }
                }

                TopicDetectionFunction topicsFunction = new TopicDetectionFunction();
                Map<Integer, Multiset<String>> topics = topicsFunction.analyze(lines, lang, userSuppliedStopwords, replaceStopwords, isScientificCorpus, precision);
                Set<Map.Entry<Integer, Multiset<String>>> entrySet = topics.entrySet();

                JsonObjectBuilder globalObject = Json.createObjectBuilder();
                for (Map.Entry<Integer, Multiset<String>> entry : entrySet) {
                    String communityName = String.valueOf((entry.getKey()));
                    Multiset<String> values = entry.getValue();
                    JsonObjectBuilder termsAndTheirCountsInOneCOmmunity = Json.createObjectBuilder();
                    for (String element : values.getElementSet()) {
                        termsAndTheirCountsInOneCOmmunity.add(element, values.getCount(element));
                    }
                    globalObject.add(communityName, termsAndTheirCountsInOneCOmmunity);
                }
                
                String jsonObjectToString = APIController.turnJsonObjectToString(globalObject.build());

                ctx.result(jsonObjectToString.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
            }
        });

        return app;

    }
}
