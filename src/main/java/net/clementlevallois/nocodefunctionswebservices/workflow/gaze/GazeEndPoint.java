package net.clementlevallois.nocodefunctionswebservices.workflow.gaze;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.util.NaiveRateLimit;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.Globals.GlobalQueryParams;
import net.clementlevallois.functions.model.WorkflowCoocProps;
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

        app.get(Globals.API_ENDPOINT_ROOT + WorkflowCoocProps.ENDPOINT, (Context ctx) -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            Map<String, List<String>> queryParamMap = ctx.queryParamMap();
            RunnableCooc workflow = parseQueryParamsForCooc(queryParamMap);
            var coocProps = new WorkflowCoocProps(APIController.tempFilesFolder);

            Map<Integer, Multiset<String>> coocsAsMap = null;

            Path pathForCooccurrencesFormattedAsLines = coocProps.getPathForCooccurrencesFormattedAsMap(workflow.getJobId());
            byte[] byteArray = Files.readAllBytes(pathForCooccurrencesFormattedAsLines);
            try (ByteArrayInputStream bis = new ByteArrayInputStream(byteArray); ObjectInputStream ois = new ObjectInputStream(bis)) {
                Object obj = ois.readObject();
                coocsAsMap = (Map<Integer, Multiset<String>>) obj;
            } catch (IOException | ClassNotFoundException ex) {
                ctx.result("error in deserializing persisted coocs to a map in job " + workflow.getJobId()).status(HttpURLConnection.HTTP_BAD_REQUEST);
            }
            workflow.setLines(coocsAsMap);
            workflow.runGazeCoocInBackgroundThread();
            ctx.result("ok").status(HttpURLConnection.HTTP_OK);
        });

        app.get(Globals.API_ENDPOINT_ROOT + WorkflowSimProps.ENDPOINT, ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            Map<String, List<String>> queryParamMap = ctx.queryParamMap();
            RunnableGazeSim simRunnable = parseQueryParamsForSim(queryParamMap);
            String jobId = simRunnable.getJobId();

            List<SheetModel> dataInSheets = null;
            Path tempDataPath = APIController.globals.getDataSheetPath(jobId);
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

            Map<String, Set<String>> sourcesAndTargets = new HashMap();
            Set<String> setTargets;
            String source = "";
            while (iterator.hasNext()) {
                Map.Entry<Integer, List<CellRecord>> entryCellRecordsInRow = iterator.next();
                setTargets = new HashSet();
                for (CellRecord cr : entryCellRecordsInRow.getValue()) {
                    if (cr.getColIndex() == simRunnable.getSourceColIndex()) {
                        source = cr.getRawValue();
                    } else {
                        setTargets.add(cr.getRawValue());
                    }
                }
                if (sourcesAndTargets.containsKey(source)) {
                    Set<String> existingTargetsForThisSource = sourcesAndTargets.get(source);
                    existingTargetsForThisSource.addAll(setTargets);
                    sourcesAndTargets.put(source, existingTargetsForThisSource);
                } else {
                    sourcesAndTargets.put(source, setTargets);
                }
            }
            if (sourcesAndTargets.isEmpty()) {
                ctx.result("no source and  / or no targets").status(HttpURLConnection.HTTP_BAD_REQUEST);
            }

            simRunnable.setSourcesAndTargets(sourcesAndTargets);
            simRunnable.runGazeSimInBackgroundThread();
            ctx.result("ok").status(HttpURLConnection.HTTP_OK);

        });
        return app;
    }

    private static RunnableCooc parseQueryParamsForCooc(Map<String, List<String>> queryParamMap) throws Exception {
        var workflow = new RunnableCooc();
        for (var entry : queryParamMap.entrySet()) {
            String key = entry.getKey();
            String decodedParamValue = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);

            Optional<GlobalQueryParams> gqp = APIController.enumValueOf(GlobalQueryParams.class, key);

            if (gqp.isPresent()) {
                Consumer<String> gqpHandler = switch (gqp.get()) {
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
