package net.clementlevallois.nocodefunctionswebservices.workflow.cowo;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowCowoProps;

import net.clementlevallois.nocodefunctionswebservices.APIController;

public class WorkflowCowoEndPoint {

    public static Javalin addAll(Javalin app) {
        app.post(Globals.API_ENDPOINT_ROOT + WorkflowCowoProps.ENDPOINT, ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            String body = ctx.body();
            if (body.isBlank()) {
                ctx.status(HttpURLConnection.HTTP_BAD_REQUEST)
                        .result(Json.createObjectBuilder()
                                .add("-99", "workflow cowo endpoint: body of the request should not be empty")
                                .build()
                                .toString());
                return;
            }
            RunnableCowoWorkflow workflow = parseBody(body);
            workflow = parseParams(workflow, ctx);
            workflow.run();
            ctx.status(HttpURLConnection.HTTP_OK).result("ok");
        });

        return app;
    }

    private static RunnableCowoWorkflow parseBody(String body) throws Exception {
        RunnableCowoWorkflow workflow = new RunnableCowoWorkflow();
        JsonReader reader = Json.createReader(new StringReader(body));
        JsonObject json = reader.readObject();

        for (var entry : json.entrySet()) {
            String key = entry.getKey();
            switch (key) {
                case "userSuppliedStopwords" -> {
                    json.getJsonObject(key).values()
                            .forEach(v -> workflow.getUserSuppliedStopwords().add(v.toString().replace("\"", "")));
                }
            }
        }
        return workflow;
    }

    private static RunnableCowoWorkflow parseParams(RunnableCowoWorkflow workflow, Context ctx) throws Exception {

        for (var entry : ctx.queryParamMap().entrySet()) {
            String key = entry.getKey();
            String decodedParamValue = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);
            switch (key) {
                case "dataPersistenceId" ->
                    handleDataPersistence(workflow, decodedParamValue);
                case "lang" ->
                    workflow.setLang(entry.getValue().getFirst());
                case "minTermFreq" ->
                    workflow.setMinTermFreq(Integer.parseInt(decodedParamValue));
                case "minCharNumber" ->
                    workflow.setMinCharNumber(Integer.parseInt(decodedParamValue));
                case "maxNGram" ->
                    workflow.setMaxNGram(Integer.parseInt(decodedParamValue));
                case "minCoocFreq" ->
                    workflow.setMinCoocFreq(Integer.parseInt(decodedParamValue));
                case "replaceStopwords" ->
                    workflow.setReplaceStopwords(Boolean.parseBoolean(decodedParamValue));
                case "removeAccents" ->
                    workflow.setRemoveAccents(Boolean.parseBoolean(decodedParamValue));
                case "lemmatize" ->
                    workflow.setLemmatize(Boolean.parseBoolean(decodedParamValue));
                case "isScientificCorpus" ->
                    workflow.setIsScientificCorpus(Boolean.parseBoolean(decodedParamValue));
                case "firstNames" ->
                    workflow.setFirstNames(Boolean.parseBoolean(decodedParamValue));
                case "typeCorrection" ->
                    workflow.setTypeCorrection(decodedParamValue);
                case "sessionId" ->
                    workflow.setSessionId(decodedParamValue);
                case "callbackURL" ->
                    workflow.setCallbackURL(decodedParamValue);
                default -> {
                    System.out.println("json key received in topics workflow api endpoint not recognized: " + key);
                }
            }
        }
        return workflow;
    }

    private static void handleDataPersistence(RunnableCowoWorkflow workflow, String dataPersistenceId) throws Exception {
        workflow.setJobId(dataPersistenceId);
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
