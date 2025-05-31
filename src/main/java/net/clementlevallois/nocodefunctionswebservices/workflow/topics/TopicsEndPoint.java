package net.clementlevallois.nocodefunctionswebservices.workflow.topics;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.Globals.GlobalQueryParams;
import net.clementlevallois.functions.model.WorkflowTopicsProps;
import net.clementlevallois.functions.model.WorkflowTopicsProps.BodyJsonKeys;
import net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams;

import net.clementlevallois.nocodefunctionswebservices.APIController;
import org.openide.util.Exceptions;

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

            Consumer<String> qpHandler = switch (QueryParams.valueOf(key.toUpperCase())) {
                case LANG ->
                    workflow::setLang;
                case PRECISION ->
                    s -> workflow.setPrecision(Integer.parseInt(s));
                case MIN_TERM_FREQ ->
                    s -> workflow.setMinTermFreq(Integer.parseInt(s));
                case MIN_CHAR_NUMBER ->
                    s -> workflow.setMinCharNumber(Integer.parseInt(s));
                case REPLACE_STOPWORDS ->
                    s -> workflow.setReplaceStopwords(Boolean.parseBoolean(s));
                case REMOVE_ACCENTS ->
                    s -> workflow.setRemoveAccents(Boolean.parseBoolean(s));
                case LEMMATIZE ->
                    s -> workflow.setLemmatize(Boolean.parseBoolean(s));
                case IS_SCIENTIFIC_CORPUS ->
                    s -> workflow.setIsScientificCorpus(Boolean.parseBoolean(s));
            };
            qpHandler.accept(decodedParamValue);

            Consumer<String> gqpHandler = switch (GlobalQueryParams.valueOf(key.toUpperCase())) {
                case SESSION_ID ->
                    workflow::setSessionId;
                case CALLBACK_URL ->
                    workflow::setCallbackURL;
                case JOB_ID ->
                    s -> handleDataPersistence(workflow, s);
            };
            gqpHandler.accept(decodedParamValue);
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

    private static void handleDataPersistence(RunnableTopicsWorkflow workflow, String jobId){
        workflow.setJobId(jobId);
        Path inputFile = APIController.tempFilesFolder.resolve(jobId).resolve(jobId);
        if (Files.exists(inputFile) && !Files.isDirectory(inputFile)) {
            try {
                List<String> lines = Files.readAllLines(inputFile, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    workflow.getLines().put(i, lines.get(i).trim());
                }
                Files.deleteIfExists(inputFile);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }
}
