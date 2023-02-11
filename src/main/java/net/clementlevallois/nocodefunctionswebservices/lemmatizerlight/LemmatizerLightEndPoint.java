/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.lemmatizerlight;

import net.clementlevallois.nocodefunctionswebservices.cowo.*;
import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.cowo.controller.CowoFunction;
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

                Lemmatizer lemmatizer = new Lemmatizer(lang);
                JsonObjectBuilder job = Json.createObjectBuilder();

                for (Map.Entry<Integer, String> entry : lines.entrySet()) {
                    String sentenceLemmatized = lemmatizer.sentenceLemmatizer(entry.getValue());
                    job.add(String.valueOf(entry.getKey()), sentenceLemmatized);
                }
                String result = APIController.turnJsonObjectToString(job.build());
                ctx.result(result.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
            }
        });

        return app;

    }
}
