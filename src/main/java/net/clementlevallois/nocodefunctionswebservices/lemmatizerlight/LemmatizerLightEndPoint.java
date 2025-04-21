/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.lemmatizerlight;

import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.lemmatizerlightweight.Lemmatizer;
import net.clementlevallois.nocodefunctionswebservices.APIController;

/**
 *
 * @author LEVALLOIS
 */
public class LemmatizerLightEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post("/api/lemmatizer_light", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            TreeMap<Integer, String> lines = new TreeMap();
            String lang = "en";

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
                }
                List<String> languages = Arrays.asList(lang.split(","));
                List<Lemmatizer> lemmatizersForOneLanguage = new ArrayList();
                for (String language : languages) {
                    lemmatizersForOneLanguage.add(new Lemmatizer(language));
                }
                JsonObjectBuilder job = Json.createObjectBuilder();

                for (Map.Entry<Integer, String> entry : lines.entrySet()) {
                    String lineToLemmatize = entry.getValue();
                    for (var lemmatizer : lemmatizersForOneLanguage) {
                        lineToLemmatize = lemmatizer.sentenceLemmatizer(lineToLemmatize);
                    }
                    job.add(String.valueOf(entry.getKey()), lineToLemmatize);
                }
                String result = APIController.turnJsonObjectToString(job.build());
                if (result == null || result.isBlank()) {
                    ctx.result("error in lemmatizer light weight on the API side".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_INTERNAL_ERROR);
                } else {
                    ctx.result(result.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
                }
            }
        });

        app.post("/api/lemmatizer_light/multiset", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            TreeMap<String, Integer> entries = new TreeMap();
            String lang = "en";

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
                            entries.put(nextLineKey, linesJson.getInt(nextLineKey));
                        }
                    }
                    if (nextKey.equals("lang")) {
                        lang = jsonObject.getString(nextKey);
                    }
                }

                Lemmatizer lemmatizer = new Lemmatizer(lang);

                Map<String, Integer> resultLemmatization = new HashMap();

                for (Map.Entry<String, Integer> entry : entries.entrySet()) {
                    String termLemmatized = lemmatizer.lemmatize(entry.getKey());
                    Integer occurrences = resultLemmatization.getOrDefault(termLemmatized, 0);
                    resultLemmatization.put(termLemmatized, occurrences + 1);
                }

                byte[] byteArraySerializerForAnyObject = APIController.byteArraySerializerForAnyObject(resultLemmatization);
                ctx.result(byteArraySerializerForAnyObject).status(HttpURLConnection.HTTP_OK);
            }
        });

        app.post("/api/lemmatizer_light/map/{lang}", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            String lang = ctx.pathParam("lang");

            byte[] bodyAsBytes = ctx.bodyAsBytes();

            if (bodyAsBytes.length == 0) {
                objectBuilder.add("-99", "body of the request should not be empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                ByteArrayInputStream bis = new ByteArrayInputStream(bodyAsBytes);
                ObjectInputStream ois = new ObjectInputStream(bis);
                TreeMap<Integer, String> mapInput = (TreeMap<Integer, String>) ois.readObject();

                Lemmatizer lemmatizer = new Lemmatizer(lang);

                Map<Integer, String> mapResult = new HashMap();

                Set<Map.Entry<Integer, String>> entrySet = mapInput.entrySet();

                for (Map.Entry<Integer, String> entry : entrySet) {
                    String lemmatized = lemmatizer.lemmatize(entry.getValue());
                    mapResult.put(entry.getKey(), lemmatized);
                }

                byte[] byteArraySerializerForAnyObject = APIController.byteArraySerializerForAnyObject(mapResult);
                ctx.result(byteArraySerializerForAnyObject).status(HttpURLConnection.HTTP_OK);
            }
        });

        return app;

    }
}
