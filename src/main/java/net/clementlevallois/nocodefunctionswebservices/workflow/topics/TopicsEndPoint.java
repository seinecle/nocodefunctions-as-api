package net.clementlevallois.nocodefunctionswebservices.workflow.topics;

import io.javalin.Javalin;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.Globals.GlobalQueryParams;
import net.clementlevallois.functions.model.WorkflowTopicsProps;
import net.clementlevallois.functions.model.WorkflowTopicsProps.BodyJsonKeys;
import net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams;

import net.clementlevallois.nocodefunctionswebservices.APIController;
import static net.clementlevallois.nocodefunctionswebservices.APIController.enumValueOf;
import org.openide.util.Exceptions;

public class TopicsEndPoint {

    public static Javalin addAll(Javalin app) {
        app.post(Globals.API_ENDPOINT_ROOT + WorkflowTopicsProps.ENDPOINT, ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            Map<String, List<String>> queryParamMap = ctx.queryParamMap();
            RunnableTopicsWorkflow workflow = parseQueryParams(queryParamMap);
            workflow.setRequestedFormats(Set.of("gexf", "json"));
            workflow.run();
            ctx.status(HttpURLConnection.HTTP_OK).result("ok");
        });

        return app;
    }

    private static RunnableTopicsWorkflow parseQueryParams(Map<String, List<String>> queryParamMap) throws Exception {
        var workflow = new RunnableTopicsWorkflow();
        for (var entry : queryParamMap.entrySet()) {
            String key = entry.getKey();
            String decodedParamValue = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);

            Optional<QueryParams> qp = enumValueOf(QueryParams.class, key);
            Optional<GlobalQueryParams> gqp = enumValueOf(GlobalQueryParams.class, key);

            if (qp.isPresent()) {
                Consumer<String> qpHandler = switch (qp.get()) {
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
                System.out.println("issue in workflow topic endpoint with unknown enum value");
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

    private static void handleDataPersistence(RunnableTopicsWorkflow workflow, String jobId) {
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
