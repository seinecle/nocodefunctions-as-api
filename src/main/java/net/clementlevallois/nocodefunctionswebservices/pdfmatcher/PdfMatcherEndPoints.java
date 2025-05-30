/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.pdfmatcher;

import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.FunctionPdfMatcher;
import net.clementlevallois.functions.model.Occurrence;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.pdfmatcher.controller.PdfMatcher;

/**
 *
 * @author LEVALLOIS
 */
public class PdfMatcherEndPoints {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post(Globals.API_ENDPOINT_ROOT+ FunctionPdfMatcher.ENDPOINT, ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            TreeMap<Integer, String> lines = new TreeMap();
            TreeMap<Integer, Integer> pages = new TreeMap();
            Integer nbWords = null;
            Integer nbLines = null;
            String searchedTerm = null;
            Boolean caseSensitive = false;
            String endOfPage = "end of page";
            String startOfPage = "start of page";

            String body = ctx.body();
            if (body.isEmpty()) {
                objectBuilder.add("-99", "body of the request should not be empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                JsonReader jsonReader = Json.createReader(new StringReader(body));
                JsonObject jsonObject = jsonReader.readObject();
                Iterator<String> iteratorKeys = jsonObject.keySet().iterator();
                int i = 0;
                while (iteratorKeys.hasNext()) {
                    String nextKey = iteratorKeys.next();
                    if (nextKey.equals("lines")) {
                        JsonObject linesJson = jsonObject.getJsonObject(nextKey);
                        for (String nextLineKey : linesJson.keySet()) {
                            lines.put(Integer.valueOf(nextLineKey), linesJson.getString(nextLineKey));
                        }
                    }
                    if (nextKey.equals("pages")) {
                        JsonObject pagesJson = jsonObject.getJsonObject(nextKey);
                        for (String nextPageKey : pagesJson.keySet()) {
                            pages.put(Integer.valueOf(nextPageKey), pagesJson.getInt(nextPageKey));
                        }
                    }
                    if (nextKey.equals("nbWords")) {
                        nbWords = jsonObject.getInt(nextKey);
                    }
                    if (nextKey.equals("nbLines")) {
                        nbLines = jsonObject.getInt(nextKey);
                    }
                    if (nextKey.equals("caseSensitive")) {
                        caseSensitive = jsonObject.getBoolean(nextKey);
                    }
                    if (nextKey.equals("searchedTerm")) {
                        searchedTerm = jsonObject.getString(nextKey);
                    }
                    if (nextKey.equals("endOfPage")) {
                        endOfPage = jsonObject.getString(nextKey);
                    }
                }

                PdfMatcher pdfMatcher = new PdfMatcher();
                List<Occurrence> occurrences = pdfMatcher.analyze(pages, searchedTerm, lines, nbWords, nbLines, caseSensitive, startOfPage, endOfPage);
                if (occurrences == null) {
                    ctx.result("error on the pdf occurrences API - occurrences were null".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_INTERNAL_ERROR);
                } else {
                    ctx.result(APIController.byteArraySerializerForListOfOccurrences(occurrences)).status(HttpURLConnection.HTTP_OK);
                }
            }
        });

        return app;

    }
}
