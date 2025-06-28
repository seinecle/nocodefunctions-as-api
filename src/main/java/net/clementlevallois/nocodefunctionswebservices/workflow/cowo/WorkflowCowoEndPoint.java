package net.clementlevallois.nocodefunctionswebservices.workflow.cowo;

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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowCowoProps;

import net.clementlevallois.nocodefunctionswebservices.APIController;
import org.openide.util.Exceptions;

public class WorkflowCowoEndPoint {

    public static Javalin addAll(Javalin app) {
        app.post(Globals.API_ENDPOINT_ROOT + WorkflowCowoProps.ENDPOINT, ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            RunnableCowoWorkflow workflow = parseParams(ctx);
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

    private static RunnableCowoWorkflow parseParams(Context ctx) throws Exception {
        var workflow = new RunnableCowoWorkflow();
        for (var entry : ctx.queryParamMap().entrySet()) {
            String key = entry.getKey();
            String decodedParamValue = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);

            Optional<WorkflowCowoProps.QueryParams> qp = APIController.enumValueOf(WorkflowCowoProps.QueryParams.class, key);
            Optional<Globals.GlobalQueryParams> gqp = APIController.enumValueOf(Globals.GlobalQueryParams.class, key);

            if (qp.isPresent()) {
                Consumer<String> qpHandler = switch (qp.get()) {
                    case LANG ->
                        workflow::setLang;
                    case MIN_CHAR_NUMBER ->
                        s -> workflow.setMinCharNumber(Integer.parseInt(s));
                    case REPLACE_STOPWORDS ->
                        s -> workflow.setReplaceStopwords(Boolean.parseBoolean(s));
                    case IS_SCIENTIFIC_CORPUS ->
                        s -> workflow.setIsScientificCorpus(Boolean.parseBoolean(s));
                    case LEMMATIZE ->
                        s -> workflow.setLemmatize(Boolean.parseBoolean(s));
                    case REMOVE_ACCENTS ->
                        s -> workflow.setRemoveAccents(Boolean.parseBoolean(s));
                    case MIN_TERM_FREQ ->
                        s -> workflow.setMinTermFreq(Integer.parseInt(s));
                    case MIN_COOC_FREQ ->
                        s -> workflow.setMinCoocFreq(Integer.parseInt(s));
                    case REMOVE_FIRST_NAMES ->
                        s -> workflow.setFirstNames(Boolean.parseBoolean(s));
                    case MAX_NGRAMS ->
                        s -> workflow.setMaxNGram(Integer.parseInt(s));
                    case TYPE_CORRECTION ->
                        workflow::setTypeCorrection;
                };
                qpHandler.accept(decodedParamValue);
            } else if (gqp.isPresent()) {
                Consumer<String> gqpHandler = switch (gqp.get()) {
                    case SESSION_ID ->
                        workflow::setSessionId;
                    case CALLBACK_URL ->
                        workflow::setCallbackURL;
                    case JOB_ID ->
                        s -> handleDataPersistence(workflow, s);
                };
                gqpHandler.accept(decodedParamValue);
            } else {
                System.out.println("Workflow Cowo endpoint: unknown query param key: " + key);
            }
        }
        return workflow;
    }

    private static void handleDataPersistence(RunnableCowoWorkflow workflow, String jobId) {
        workflow.setJobId(jobId);
        Path inputFile = APIController.globals.getInputDataPath(jobId);
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
