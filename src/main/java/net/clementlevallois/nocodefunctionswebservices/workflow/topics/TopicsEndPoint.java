package net.clementlevallois.nocodefunctionswebservices.workflow.topics;

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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.Globals.GlobalQueryParams;
import net.clementlevallois.functions.model.WorkflowTopicsProps;
import net.clementlevallois.functions.model.WorkflowTopicsProps.BodyJsonKeys;
import net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams; // Import the enum

import net.clementlevallois.nocodefunctionswebservices.APIController;

public class TopicsEndPoint {

    public static Javalin addAll(Javalin app) {
        app.post(Globals.API_ENDPOINT_ROOT + WorkflowTopicsProps.ENDPOINT, ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            String body = ctx.body();
            if (body.isBlank()) {
                ctx.status(HttpURLConnection.HTTP_BAD_REQUEST)
                        .result(Json.createObjectBuilder()
                                .add("-99", "topics endpoint: body of the request should not be empty")
                                .build()
                                .toString());
                return;
            }

            RunnableTopicsWorkflow workflow = parseBody(body);
            workflow = parseQueryParams(workflow, ctx);
            workflow.setRequestedFormats(Set.of("gexf", "json"));
            workflow.run();
            ctx.status(HttpURLConnection.HTTP_OK).result("ok");
        });

        return app;
    }

    private static RunnableTopicsWorkflow parseQueryParams(RunnableTopicsWorkflow workflow, Context ctx) throws Exception {

        for (var entry : ctx.queryParamMap().entrySet()) {
            String key = entry.getKey();
            String decodedParamValue = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);

            switch (QueryParams.valueOf(key.toUpperCase())) { // Convert key to uppercase to match enum names
                case LANG ->
                    workflow.setLang(decodedParamValue);
                case PRECISION ->
                    workflow.setPrecision(Integer.parseInt(decodedParamValue));
                case MIN_TERM_FREQ ->
                    workflow.setMinTermFreq(Integer.parseInt(decodedParamValue));
                case MIN_CHAR_NUMBER ->
                    workflow.setMinCharNumber(Integer.parseInt(decodedParamValue));
                case REPLACE_STOPWORDS ->
                    workflow.setReplaceStopwords(Boolean.parseBoolean(decodedParamValue));
                case REMOVE_ACCENTS ->
                    workflow.setRemoveAccents(Boolean.parseBoolean(decodedParamValue));
                case LEMMATIZE ->
                    workflow.setLemmatize(Boolean.parseBoolean(decodedParamValue));
                case IS_SCIENTIFIC_CORPUS ->
                    workflow.setIsScientificCorpus(Boolean.parseBoolean(decodedParamValue));
            }

            switch (GlobalQueryParams.valueOf(key.toUpperCase())) {
                case SESSION_ID ->
                    workflow.setSessionId(decodedParamValue);
                case CALLBACK_URL ->
                    workflow.setCallbackURL(decodedParamValue);
                case JOB_ID ->
                    handleDataPersistence(workflow, decodedParamValue);
            }
        }
        return workflow;
    }

    private static RunnableTopicsWorkflow parseBody(String body) throws Exception {
        RunnableTopicsWorkflow workflow = new RunnableTopicsWorkflow();
        JsonReader reader = Json.createReader(new StringReader(body));
        JsonObject json = reader.readObject();
        for (var entry : json.entrySet()) {
            String key = entry.getKey();
            switch (BodyJsonKeys.valueOf(key.toUpperCase())) {
                case USER_SUPPLIED_STOPWORDS -> {
                    json.getJsonObject(key).values()
                            .forEach(v -> workflow.getUserSuppliedStopwords().add(v.toString().replace("\"", "")));
                }
            }
        }
        return workflow;
    }

    private static void handleDataPersistence(RunnableTopicsWorkflow workflow, String jobId) throws Exception {
        workflow.setJobId(jobId);
        Path inputFile = APIController.tempFilesFolder.resolve(jobId).resolve(jobId);
        if (Files.exists(inputFile) && !Files.isDirectory(inputFile)) {
            List<String> lines = Files.readAllLines(inputFile, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                workflow.getLines().put(i, lines.get(i).trim());
            }
            Files.deleteIfExists(inputFile);
        }
    }
}
