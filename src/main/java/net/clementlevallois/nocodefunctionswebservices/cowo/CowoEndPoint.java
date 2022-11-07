/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.cowo;

import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.cowo.controller.CowoFunction;

/**
 *
 * @author LEVALLOIS
 */
public class CowoEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post("/api/cowo", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            TreeMap<Integer, String> lines = new TreeMap();
            Set<String> userSuppliedStopwords = new HashSet();
            String lang = "en";
            String typeCorrection = "none";
            int minCharNumber = 5;
            int minCoocFreq = 3;
            int minTermFreq = 3;
            boolean replaceStopwords = false;
            boolean isScientificCorpus = false;

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
                    if (nextKey.equals("minCharNumber")) {
                        minCharNumber = jsonObject.getInt(nextKey);
                    }
                    if (nextKey.equals("minCoocFreq")) {
                        minCoocFreq = jsonObject.getInt(nextKey);
                    }
                    if (nextKey.equals("minTermFreq")) {
                        minTermFreq = jsonObject.getInt(nextKey);
                    }
                    if (nextKey.equals("replaceStopwords")) {
                        replaceStopwords = jsonObject.getBoolean(nextKey);
                    }
                    if (nextKey.equals("isScientificCorpus")) {
                        isScientificCorpus = jsonObject.getBoolean(nextKey);
                    }
                    if (nextKey.equals("typeCorrection")) {
                        typeCorrection = jsonObject.getString(nextKey);
                    }
                }

                CowoFunction cowoFunction = new CowoFunction();
                String gexf = cowoFunction.analyze(lines, lang, userSuppliedStopwords, minCharNumber, replaceStopwords, isScientificCorpus, minCoocFreq, minTermFreq, typeCorrection);
                ctx.result(gexf.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
            }
        });

        return app;

    }
}
