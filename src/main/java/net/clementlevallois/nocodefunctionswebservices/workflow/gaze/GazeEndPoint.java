package net.clementlevallois.nocodefunctionswebservices.workflow.gaze;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.Globals.GlobalQueryParams;
import net.clementlevallois.functions.model.WorkflowGazeProps;
import net.clementlevallois.functions.model.WorkflowGazeProps.BodyJsonKeys;
import static net.clementlevallois.functions.model.WorkflowGazeProps.BodyJsonKeys.LINES;
import net.clementlevallois.functions.model.WorkflowGazeProps.QueryParams;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import static net.clementlevallois.nocodefunctionswebservices.APIController.enumValueOf;
import net.clementlevallois.utils.Multiset;

/**
 *
 * @author LEVALLOIS
 */
public class GazeEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post(Globals.API_ENDPOINT_ROOT + WorkflowGazeProps.ENDPOINT_COOC, (Context ctx) -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            byte[] bodyAsBytes = ctx.bodyAsBytes();
            String body = new String(bodyAsBytes, StandardCharsets.UTF_8);
            if (body.isEmpty()) {
                String errorMsg = "body of the request should not be empty";
                ctx.result(errorMsg).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                RunnableGazeCooc runnableCooc = parseBodyForCooc(body);
                Map<String, List<String>> queryParamMap = ctx.queryParamMap();
                runnableCooc = parseQueryParamsForCooc(runnableCooc, queryParamMap);
                runnableCooc.runGazeCoocInBackgroundThread();
                ctx.result("ok").status(HttpURLConnection.HTTP_OK);
            }
        });

        app.post(Globals.API_ENDPOINT_ROOT + WorkflowGazeProps.ENDPOINT_SIM, ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            byte[] bodyAsBytes = ctx.bodyAsBytes();

            String body = new String(bodyAsBytes, StandardCharsets.UTF_8);
            if (body.isEmpty()) {
                String errorMsg = "body of the request should not be empty";
                ctx.result(errorMsg).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                RunnableGazeSim gazeSimRunnable = parseBodyForSim(body);
                Map<String, List<String>> queryParamMap = ctx.queryParamMap();
                gazeSimRunnable = parseQueryParamsForSim(gazeSimRunnable, queryParamMap);
                gazeSimRunnable.runGazeSimInBackgroundThread();
                ctx.result("ok").status(HttpURLConnection.HTTP_OK);
            }
        });
        return app;
    }

    private static RunnableGazeCooc parseBodyForCooc(String body) throws Exception {
        RunnableGazeCooc workflow = new RunnableGazeCooc();
        JsonReader reader = Json.createReader(new StringReader(body));
        JsonObject json = reader.readObject();
        for (var entry : json.entrySet()) {
            String key = entry.getKey();
            switch (BodyJsonKeys.valueOf(key.toUpperCase())) {
                case LINES -> {
                    JsonObject linesJson = json.getJsonObject(key);
                    Map<Integer, Multiset<String>> lines = new HashMap();
                    for (String nextLineKey : linesJson.keySet()) {
                        JsonArray jsonArray = linesJson.getJsonArray(nextLineKey);
                        List<String> list = new ArrayList();
                        for (int i = 0; i < jsonArray.size(); i++) {
                            list.add(jsonArray.getString(i));
                        }
                        Multiset multiset = new Multiset();
                        multiset.addAllFromListOrSet(list);
                        lines.put(Integer.valueOf(nextLineKey), multiset);
                    }
                    workflow.setLines(lines);
                }
            }
        }
        return workflow;
    }

    private static RunnableGazeSim parseBodyForSim(String body) throws Exception {
        RunnableGazeSim gazeSimRunnable = new RunnableGazeSim();
        JsonReader jsonReader = Json.createReader(new StringReader(body));
        JsonObject jsonObject = jsonReader.readObject();
        for (var entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            switch (BodyJsonKeys.valueOf(key.toUpperCase())) {
                case LINES -> {
                    JsonObject linesJson = jsonObject.getJsonObject(key);
                    Map<String, Set<String>> lines = new TreeMap();
                    for (String nextLineKey : linesJson.keySet()) {
                        JsonArray jsonArray = linesJson.getJsonArray(nextLineKey);
                        List<String> list = new ArrayList();
                        for (int i = 0; i < jsonArray.size(); i++) {
                            list.add(jsonArray.getString(i));
                        }
                        Set<String> set = new HashSet();
                        set.addAll(list);
                        lines.put(nextLineKey, set);
                    }
                    gazeSimRunnable.setLines(lines);
                }
            }
        }
        return gazeSimRunnable;
    }

    private static RunnableGazeCooc parseQueryParamsForCooc(RunnableGazeCooc workflow, Map<String, List<String>> queryParamMap) throws Exception {
        for (var entry : queryParamMap.entrySet()) {
            String key = entry.getKey();
            String decodedParamValue = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);

            Optional<GlobalQueryParams> gqp = APIController.enumValueOf(GlobalQueryParams.class, key);

            if (gqp.isPresent()) {
                Consumer<String> gqpHandler = switch (gqp.get()) {
                    case SESSION_ID ->
                        workflow::setSessionId;
                    case CALLBACK_URL ->
                        workflow::setCallbackURL;
                    case JOB_ID ->
                        workflow::setJobId;
                };
                gqpHandler.accept(decodedParamValue);
            } else {
                System.out.println("issue in workflow gaze cooc endpoint with unknown enum value");
            }
        }
        return workflow;
    }

    private static RunnableGazeSim parseQueryParamsForSim(RunnableGazeSim workflow, Map<String, List<String>> queryParamMap) throws Exception {
        for (var entry : queryParamMap.entrySet()) {
            String key = entry.getKey();
            String decodedParamValue = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);

            Optional<QueryParams> qp = enumValueOf(QueryParams.class, key);
            Optional<GlobalQueryParams> gqp = enumValueOf(GlobalQueryParams.class, key);

            if (qp.isPresent()) {
                Consumer<String> qpHandler = switch (qp.get()) {
                    case MIN_SHARED_TARGETS ->
                        s -> workflow.setMinSharedTarget(Integer.parseInt((s)));
                };
                qpHandler.accept(decodedParamValue);
            } else if (gqp.isPresent()) {
                Consumer<String> gqpHandler = switch (gqp.get()) {
                    case SESSION_ID ->
                        workflow::setSessionId;
                    case CALLBACK_URL ->
                        workflow::setCallbackURL;
                    case JOB_ID ->
                        workflow::setJobId;
                };
                gqpHandler.accept(decodedParamValue);
            } else {
                System.out.println("issue in workflow sim gaze endpoint with unknown enum value");
            }
        }
        return workflow;
    }
}
