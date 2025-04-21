/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.workflow.topics;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.nocodefunctionswebservices.APIController;

/**
 *
 * @author LEVALLOIS
 */
public class TopicsEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post("/api/workflow/topics", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            
            RunnableTopicsWorkflow runnableTopics = new RunnableTopicsWorkflow();
            
            byte[] bodyAsBytes = ctx.bodyAsBytes();
            String body = new String(bodyAsBytes, StandardCharsets.US_ASCII);
            if (body.isEmpty()) {
                objectBuilder.add("-99", "topics endpoint: body of the request should not be empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                JsonReader jsonReader = Json.createReader(new StringReader(body));
                JsonObject jsonObject = jsonReader.readObject();
                for (String nextKey : jsonObject.keySet()) {
                    if (nextKey.equals("dataPersistenceId")) {
                        runnableTopics.setDataPersistenceId(jsonObject.getString(nextKey));
                        Path tempDataPathForThisTask = Path.of(APIController.tempFilesFolder.toString(), runnableTopics.getDataPersistenceId());
                        Path inputDataForThisTask = Path.of(tempDataPathForThisTask.toString(), runnableTopics.getDataPersistenceId());
                        if (Files.exists(inputDataForThisTask) && !Files.isDirectory(inputDataForThisTask)) {
                            List<String> readAllLines = Files.readAllLines(inputDataForThisTask, StandardCharsets.UTF_8);
                            int i = 0;
                            for (String line : readAllLines) {
                                runnableTopics.getLines().put(i++, line.trim());
                            }
                            Files.delete(inputDataForThisTask);
                        }
                    }
                    if (nextKey.equals("lang")) {
                        runnableTopics.setLang(jsonObject.getString(nextKey));
                    }
                    if (nextKey.equals("userSuppliedStopwords")) {
                        JsonObject linesJson = jsonObject.getJsonObject(nextKey);
                        for (String nextLineKey : linesJson.keySet()) {
                            runnableTopics.getUserSuppliedStopwords().add(linesJson.getString(nextLineKey));
                        }
                    }
                    if (nextKey.equals("precision")) {
                        runnableTopics.setPrecision(jsonObject.getInt(nextKey));
                    }
                    if (nextKey.equals("minTermFreq")) {
                        runnableTopics.setMinTermFreq(jsonObject.getInt(nextKey));
                    }
                    if (nextKey.equals("minCharNumber")) {
                        runnableTopics.setMinCharNumber(jsonObject.getInt(nextKey));
                    }
                    if (nextKey.equals("replaceStopwords")) {
                        runnableTopics.setReplaceStopwords(jsonObject.getBoolean(nextKey));
                    }
                    if (nextKey.equals("removeAccents")) {
                        runnableTopics.setRemoveAccents(jsonObject.getBoolean(nextKey));
                    }
                    if (nextKey.equals("lemmatize")) {
                        runnableTopics.setLemmatize(jsonObject.getBoolean(nextKey));
                    }
                    if (nextKey.equals("isScientificCorpus")) {
                        runnableTopics.setIsScientificCorpus(jsonObject.getBoolean(nextKey));
                    }
                    if (nextKey.equals("sessionId")) {
                        runnableTopics.setSessionId(jsonObject.getString(nextKey));
                    }
                    if (nextKey.equals("callbackURL")) {
                        runnableTopics.setCallbackURL(jsonObject.getString(nextKey));
                    }
                    if (nextKey.equals("dataPersistenceId")) {
                        runnableTopics.setDataPersistenceId(jsonObject.getString(nextKey));
                    }
                }
                Set<String> requestedFormats = Set.of("gexf", "excel");
                runnableTopics.setRequestedFormats(requestedFormats);
                runnableTopics.run();
                
                ctx.result("ok").status(HttpURLConnection.HTTP_OK);
            }
        });

        return app;

    }
}
