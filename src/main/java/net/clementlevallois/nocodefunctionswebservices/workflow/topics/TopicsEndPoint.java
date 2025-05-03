package net.clementlevallois.nocodefunctionswebservices.workflow.topics;

import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
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

public class TopicsEndPoint {

    public static Javalin addAll(Javalin app) {
        app.post("/api/workflow/community-insights", ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            String body = ctx.body();
            if (body.isBlank()) {
                ctx.status(HttpURLConnection.HTTP_BAD_REQUEST)
                   .result(Json.createObjectBuilder()
                               .add("-99", "community insights endpoint: body of the request should not be empty")
                               .build()
                               .toString());
                return;
            }

            RunnableTopicsWorkflow workflow = parseRequest(body);
            workflow.setRequestedFormats(Set.of("gexf", "excel"));
            workflow.run();
            ctx.status(HttpURLConnection.HTTP_OK).result("ok");
        });

        return app;
    }

    private static RunnableTopicsWorkflow parseRequest(String body) throws Exception {
        RunnableTopicsWorkflow workflow = new RunnableTopicsWorkflow();
        JsonReader reader = Json.createReader(new StringReader(body));
        JsonObject json = reader.readObject();

        for (var entry : json.entrySet()) {
            String key = entry.getKey();
            switch (key) {
                case "dataPersistenceId" -> handleDataPersistence(workflow, json.getString(key));
                case "lang" -> workflow.setLang(json.getString(key));
                case "userSuppliedStopwords" -> {
                    json.getJsonObject(key).values()
                        .forEach(v -> workflow.getUserSuppliedStopwords().add(v.toString().replace("\"", "")));
                }
                case "precision" -> workflow.setPrecision(json.getInt(key));
                case "minTermFreq" -> workflow.setMinTermFreq(json.getInt(key));
                case "minCharNumber" -> workflow.setMinCharNumber(json.getInt(key));
                case "replaceStopwords" -> workflow.setReplaceStopwords(json.getBoolean(key));
                case "removeAccents" -> workflow.setRemoveAccents(json.getBoolean(key));
                case "lemmatize" -> workflow.setLemmatize(json.getBoolean(key));
                case "isScientificCorpus" -> workflow.setIsScientificCorpus(json.getBoolean(key));
                case "sessionId" -> workflow.setSessionId(json.getString(key));
                case "callbackURL" -> workflow.setCallbackURL(json.getString(key));
                default -> {
                    System.out.println("json key received in topics workflow api endpoint not recognized");
                }
            }
        }
        return workflow;
    }

    private static void handleDataPersistence(RunnableTopicsWorkflow workflow, String dataPersistenceId) throws Exception {
        workflow.setDataPersistenceId(dataPersistenceId);
        Path inputFile = APIController.tempFilesFolder.resolve(dataPersistenceId).resolve(dataPersistenceId);
        if (Files.exists(inputFile) && !Files.isDirectory(inputFile)) {
            List<String> lines = Files.readAllLines(inputFile, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                workflow.getLines().put(i, lines.get(i).trim());
            }
            Files.deleteIfExists(inputFile);
        }
    }
}
