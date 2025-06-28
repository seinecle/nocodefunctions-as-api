package net.clementlevallois.nocodefunctionswebservices.workflow.gaze;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.Globals.GlobalQueryParams;
import net.clementlevallois.functions.model.WorkflowCoocProps;
import net.clementlevallois.functions.model.WorkflowCoocProps.BodyJsonKeys;
import net.clementlevallois.functions.model.WorkflowSimProps;
import net.clementlevallois.functions.model.WorkflowSimProps.QueryParams;
import net.clementlevallois.importers.model.CellRecord;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import static net.clementlevallois.nocodefunctionswebservices.APIController.enumValueOf;
import net.clementlevallois.utils.Multiset;

/**
 *
 * @author LEVALLOIS
 */
public class GazeEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post(Globals.API_ENDPOINT_ROOT + WorkflowCoocProps.ENDPOINT, (Context ctx) -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            Map<String, List<String>> queryParamMap = ctx.queryParamMap();
            RunnableCooc workflow = parseQueryParamsForCooc(queryParamMap);
            var coocProps = new WorkflowCoocProps(APIController.tempFilesFolder);

            JsonObject deserializedJsonObject = null;
            Path pathForCooccurrencesFormattedAsJson = coocProps.getPathForCooccurrencesFormattedAsJson(workflow.getJobId());
            try (InputStream is = Files.newInputStream(pathForCooccurrencesFormattedAsJson); JsonReader reader = Json.createReader(is)) {
                deserializedJsonObject = reader.readObject();
            } catch (IOException e) {
                System.out.println("error deserializing the coocs in json format");
                ctx.result("error deserializing the coocs in json format").status(HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }
            if (deserializedJsonObject == null) {
                System.out.println("error deserializing the coocs in json format");
                ctx.result("error deserializing the coocs in json format").status(HttpURLConnection.HTTP_BAD_REQUEST);
                return;

            }
            workflow = parseBodyForCooc(workflow, deserializedJsonObject);
            workflow.runGazeCoocInBackgroundThread();
            ctx.result("ok").status(HttpURLConnection.HTTP_OK);
        });

        app.post(Globals.API_ENDPOINT_ROOT + WorkflowSimProps.ENDPOINT, ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            Map<String, List<String>> queryParamMap = ctx.queryParamMap();
            RunnableGazeSim simRunnable = parseQueryParamsForSim(queryParamMap);
            String jobId = simRunnable.getJobId();

            List<SheetModel> dataInSheets = null;
            Path tempDataPath = APIController.globals.getDataSheetPath(jobId, jobId);
            byte[] byteArray = Files.readAllBytes(tempDataPath);
            try (ByteArrayInputStream bis = new ByteArrayInputStream(byteArray); ObjectInputStream ois = new ObjectInputStream(bis)) {
                Object obj = ois.readObject();
                if (obj instanceof List) {
                    dataInSheets = (List<SheetModel>) obj;
                } else {
                    System.out.println("Deserialized object is not a List.");
                    ctx.result("Deserialized object is not a List.").status(HttpURLConnection.HTTP_BAD_REQUEST);
                    return;
                }
            }
            SheetModel sheetWithData = dataInSheets.get(0);
            if (sheetWithData == null) {
                ctx.result("no sheetModel in List of sheetModels").status(HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }
            Map<Integer, List<CellRecord>> mapOfCellRecordsPerRow = sheetWithData.getRowIndexToCellRecords();
            Iterator<Map.Entry<Integer, List<CellRecord>>> iterator = mapOfCellRecordsPerRow.entrySet().iterator();

            Map<String, Multiset<String>> sourcesAndTargets = new HashMap();
            Multiset<String> setTargets;
            String source = "";
            while (iterator.hasNext()) {
                Map.Entry<Integer, List<CellRecord>> entryCellRecordsInRow = iterator.next();
                setTargets = new Multiset();
                for (CellRecord cr : entryCellRecordsInRow.getValue()) {
                    if (cr.getColIndex() == simRunnable.getSourceColIndex()) {
                        source = cr.getRawValue();
                    } else {
                        setTargets.addOne(cr.getRawValue());
                    }
                }
                if (sourcesAndTargets.containsKey(source)) {
                    Multiset<String> existingTargetsForThisSource = sourcesAndTargets.get(source);
                    existingTargetsForThisSource.addAllFromMultiset(setTargets);
                    sourcesAndTargets.put(source, existingTargetsForThisSource);
                } else {
                    sourcesAndTargets.put(source, setTargets);
                }
            }
            if (sourcesAndTargets.isEmpty()) {
                ctx.result("no source and  / or no targets").status(HttpURLConnection.HTTP_BAD_REQUEST);
            }

            simRunnable.runGazeSimInBackgroundThread();
            ctx.result("ok").status(HttpURLConnection.HTTP_OK);

        });
        return app;
    }

    private static RunnableCooc parseBodyForCooc(RunnableCooc workflow, JsonObject json) throws Exception {
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

    private static RunnableCooc parseQueryParamsForCooc(Map<String, List<String>> queryParamMap) throws Exception {
        var workflow = new RunnableCooc();
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

    private static RunnableGazeSim parseQueryParamsForSim(Map<String, List<String>> queryParamMap) throws Exception {
        var workflow = new RunnableGazeSim();
        for (var entry : queryParamMap.entrySet()) {
            String key = entry.getKey();
            String decodedParamValue = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);

            Optional<QueryParams> qp = enumValueOf(QueryParams.class, key);
            Optional<GlobalQueryParams> gqp = enumValueOf(GlobalQueryParams.class, key);

            if (qp.isPresent()) {
                Consumer<String> qpHandler = switch (qp.get()) {
                    case MIN_SHARED_TARGETS ->
                        s -> workflow.setMinSharedTarget(Integer.parseInt((s)));
                    case SOURCE_COL_INDEX ->
                        s -> workflow.setSourceColIndex(Integer.parseInt((s)));
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
