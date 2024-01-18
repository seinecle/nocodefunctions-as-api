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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.nocodefunctionswebservices.APIController;

/**
 *
 * @author LEVALLOIS
 */
public class CowoEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post("/api/cowo", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            RunnableCowo runnableCowo = new RunnableCowo();

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
                    if (nextKey.equals("dataPersistenceId")) {
                        runnableCowo.setDataPersistenceId(jsonObject.getString(nextKey));
                        Path tempDataPath = Path.of(APIController.tempFilesFolder.toString(), runnableCowo.getDataPersistenceId());
                        if (Files.exists(tempDataPath)) {
                            List<String> readAllLines = Files.readAllLines(tempDataPath, StandardCharsets.UTF_8);
                            int i = 0;
                            for (String line : readAllLines) {
                                runnableCowo.getLines().put(i++, line.trim());
                            }
                            Files.delete(tempDataPath);
                        }
                    }
                    if (nextKey.equals("lang")) {
                        runnableCowo.setLang(jsonObject.getString(nextKey));
                    }
                    if (nextKey.equals("userSuppliedStopwords")) {
                        JsonObject linesJson = jsonObject.getJsonObject(nextKey);
                        for (String nextLineKey : linesJson.keySet()) {
                            runnableCowo.getUserSuppliedStopwords().add(linesJson.getString(nextLineKey));
                        }
                    }
                    if (nextKey.equals("minCharNumber")) {
                        runnableCowo.setMinCharNumber(jsonObject.getInt(nextKey));
                    }
                    if (nextKey.equals("removeAccents")) {
                        runnableCowo.setRemoveAccents(jsonObject.getBoolean(nextKey));
                    }
                    if (nextKey.equals("lemmatize")) {
                        runnableCowo.setLemmatize(jsonObject.getBoolean(nextKey));
                    }
                    if (nextKey.equals("minCoocFreq")) {
                        runnableCowo.setMinCoocFreq(jsonObject.getInt(nextKey));
                    }
                    if (nextKey.equals("minTermFreq")) {
                        runnableCowo.setMinTermFreq(jsonObject.getInt(nextKey));
                    }
                    if (nextKey.equals("maxNGram")) {
                        runnableCowo.setMaxNGram(jsonObject.getInt(nextKey));
                    }
                    if (nextKey.equals("replaceStopwords")) {
                        runnableCowo.setReplaceStopwords(jsonObject.getBoolean(nextKey));
                    }
                    if (nextKey.equals("isScientificCorpus")) {
                        runnableCowo.setIsScientificCorpus(jsonObject.getBoolean(nextKey));
                    }
                    if (nextKey.equals("typeCorrection")) {
                        runnableCowo.setTypeCorrection(jsonObject.getString(nextKey));
                    }
                    if (nextKey.equals("sessionId")) {
                        runnableCowo.setSessionId(jsonObject.getString(nextKey));
                    }
                    if (nextKey.equals("callbackURL")) {
                        runnableCowo.setCallbackURL(jsonObject.getString(nextKey));
                    }
                    if (nextKey.equals("dataPersistenceId")) {
                        runnableCowo.setDataPersistenceId(jsonObject.getString(nextKey));
                    }
                }

                runnableCowo.runCowoInBackgroundThread();
                ctx.result("ok").status(HttpURLConnection.HTTP_OK);
            }
        });

        return app;

    }
}
