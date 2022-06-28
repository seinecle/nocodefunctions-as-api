/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.pdfmatcher;

import io.javalin.Javalin;
import io.javalin.http.HttpCode;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.pdfmatcher.controller.Occurrence;
import net.clementlevallois.pdfmatcher.controller.PdfMatcher;

/**
 *
 * @author LEVALLOIS
 */
public class PdfMatcherEndPoints {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post("/api/pdfmatcher", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            TreeMap<Integer, String> lines = new TreeMap();
            TreeMap<Integer, Integer> pages = new TreeMap();
            Integer nbContext = null;
            String searchedTerm = null;

            String body = ctx.body();
            if (body.isEmpty()) {
                objectBuilder.add("-99", "body of the request should not be empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpCode.BAD_REQUEST);
            } else {
                JsonReader jsonReader = Json.createReader(new StringReader(body));
                JsonObject jsonObject = jsonReader.readObject();
                Iterator<String> iteratorKeys = jsonObject.keySet().iterator();
                int i = 0;
                while (iteratorKeys.hasNext()) {
                    String nextKey = iteratorKeys.next();
                    if (nextKey.equals("lines")) {
                        JsonObject linesJson = jsonObject.getJsonObject(nextKey);
                        Iterator<String> iteratorLines = linesJson.keySet().iterator();
                        while (iteratorLines.hasNext()) {
                            String nextLineKey = iteratorLines.next();
                            lines.put(Integer.valueOf(nextLineKey), linesJson.getString(nextLineKey));
                        }
                    }
                    if (nextKey.equals("pages")) {
                        JsonObject pagesJson = jsonObject.getJsonObject(nextKey);
                        Iterator<String> iteratorPages = pagesJson.keySet().iterator();
                        while (iteratorPages.hasNext()) {
                            String nextPageKey = iteratorPages.next();
                            pages.put(Integer.valueOf(nextPageKey), pagesJson.getInt(nextPageKey));
                        }
                    }
                    if (nextKey.equals("nbContext")) {
                        nbContext = jsonObject.getInt(nextKey);
                    }                    
                    if (nextKey.equals("searchedTerm")) {
                        searchedTerm = jsonObject.getString(nextKey);
                    }
                }

                PdfMatcher pdfMatcher = new PdfMatcher();
                List<Occurrence> occurrences = pdfMatcher.analyze(pages, searchedTerm, lines, nbContext);
                ctx.result(APIController.byteArraySerializerForListOfOccurrences(occurrences)).status(HttpCode.OK);
            }
        });

        return app;

    }
}
