/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.graphops;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.clementlevallois.functions.model.FunctionSpatialization;
import static net.clementlevallois.functions.model.FunctionSpatialization.QueryParams.DURATION_IN_SECONDS;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.spatialize.controller.SpatializeFunction;

/**
 *
 * @author LEVALLOIS
 */
public class GraphOpsEndPoint {

    public static Javalin addAll(Javalin app) {

        app.get("/api/graphops/topnodes", (Context ctx) -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            String jobId = ctx.queryParam("dataPersistenceId");
            Path tempDataPath = Path.of(APIController.tempFilesFolder.toString(), jobId + "_result");
            String gexfAsString = "";
            try {
                gexfAsString = Files.readString(tempDataPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                objectBuilder.add("-99", "gexf file not readable on disk");
                objectBuilder.add("gexf file: ", tempDataPath.toString());
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }
            String nbNodes = ctx.queryParam("nbNodes");
            String callbackURL = ctx.queryParam("callbackURL");
            Integer nbNodesAsInteger;
            try {
                nbNodesAsInteger = Integer.valueOf(nbNodes);
            } catch (NumberFormatException e) {
                nbNodesAsInteger = 30;
            }
            var runnable = new RunnableGetTopNodesFromGraph(jobId);
            runnable.setGexfAsString(gexfAsString);
            runnable.runTopNodesInBackgroundThread(nbNodesAsInteger);
            ctx.result("OK".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
        });

        app.get("/api/graphops/keynodes", (Context ctx) -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            String dataPersistenceId = ctx.queryParam("dataPersistenceId");
            Path tempDataPath = Path.of(APIController.tempFilesFolder.toString(), dataPersistenceId + "_result");
            String gexfAsString = "";
            try {
                gexfAsString = Files.readString(tempDataPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                objectBuilder.add("-99", "gexf file not readable on disk");
                objectBuilder.add("gexf file: ", tempDataPath.toString());
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }
            String userSuppliedCommunityFieldName = ctx.queryParam("userSuppliedCommunityFieldName");
            String maxTopNodesPerCommunity = ctx.queryParam("maxKeyNodesPerCommunity");
            String minCommunitySize = ctx.queryParam("minCommunitySize");
            String callbackURL = ctx.queryParam("callbackURL");

            Integer maxTopNodesPerCommunityAsInteger;
            try {
                maxTopNodesPerCommunityAsInteger = Integer.valueOf(maxTopNodesPerCommunity);
            } catch (NumberFormatException e) {
                maxTopNodesPerCommunityAsInteger = 5;
            }
            Integer minCommunitySizeAsInteger;
            try {
                minCommunitySizeAsInteger = Integer.valueOf(minCommunitySize);
            } catch (NumberFormatException e) {
                minCommunitySizeAsInteger = 15;
            }
            var keyNodesOps = new RunnableGetKeyNodes();
            keyNodesOps.setCallbackURL(callbackURL);
            keyNodesOps.setDataPersistenceId(dataPersistenceId);
            keyNodesOps.setGexfAsString(gexfAsString);
            keyNodesOps.runKeyNodesInBackgroundThread(userSuppliedCommunityFieldName, maxTopNodesPerCommunityAsInteger, minCommunitySizeAsInteger);
            ctx.result("OK".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
        });

        app.get("/api/graphops/textualSummaryPerCommunity", (Context ctx) -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            String jobId = ctx.queryParam("dataPersistenceId");
            Path tempDataPath = Path.of(APIController.tempFilesFolder.toString(), jobId + "_result");
            String gexfAsString = "";
            try {
                gexfAsString = Files.readString(tempDataPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                objectBuilder.add("-99", "gexf file not readable on disk");
                objectBuilder.add("gexf file: ", tempDataPath.toString());
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }
            String userSuppliedCommunityFieldName = ctx.queryParam("userSuppliedCommunityFieldName");
            String maxTextLengthPerCommunity = ctx.queryParam("maxTextLengthPerCommunity");
            String textualAttribute = ctx.queryParam("textualAttribute");
            String minCommunitySize = ctx.queryParam("minCommunitySize");
            String maxTopNodes = ctx.queryParam("maxTopNodes");
            String callBackURL = ctx.queryParam("callBackURL");
            Integer maxTextLengthPerCommunityAsInteger;
            try {
                maxTextLengthPerCommunityAsInteger = Integer.valueOf(maxTextLengthPerCommunity);
            } catch (NumberFormatException e) {
                maxTextLengthPerCommunityAsInteger = 500;
            }
            Integer minCommunitySizeAsInteger;
            try {
                minCommunitySizeAsInteger = Integer.valueOf(minCommunitySize);
            } catch (NumberFormatException e) {
                minCommunitySizeAsInteger = 20;
            }
            Integer maxTopNodesAsInteger;
            try {
                maxTopNodesAsInteger = Integer.valueOf(maxTopNodes);
            } catch (NumberFormatException e) {
                maxTopNodesAsInteger = 50;
            }
            var textualSummaryPerCommunity = new RunnableGetTextPerCommunity();
            textualSummaryPerCommunity.setCallbackURL(callBackURL);
            textualSummaryPerCommunity.setDataPersistenceId(jobId);
            textualSummaryPerCommunity.setGexfAsString(gexfAsString);
            textualSummaryPerCommunity.runTextPerCommunityInBackgroundThread(userSuppliedCommunityFieldName, textualAttribute, maxTopNodesAsInteger, minCommunitySizeAsInteger, maxTextLengthPerCommunityAsInteger);
            ctx.result("OK".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
        });

        app.post("/api/" + FunctionSpatialization.ENDPOINT, ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 1, TimeUnit.SECONDS);
            byte[] bodyAsBytes = ctx.bodyAsBytes();
            if (bodyAsBytes.length == 0) {
                objectBuilder.add("-99", "body of the request should not be empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                String gexf = new String(bodyAsBytes, StandardCharsets.UTF_8);
                SpatializeFunction spatializer = new SpatializeFunction();
                SpatializeRequest spatializeRequest = parseParamsSpatialization(ctx);
                String gexfSpatialized = spatializer.spatialize(gexf, spatializeRequest.getSeconds());

                ctx.result(gexfSpatialized.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
            }
        }
        );
        return app;
    }

    private static SpatializeRequest parseParamsSpatialization(Context ctx) throws Exception {
        var sr = new SpatializeRequest();
        for (var entry : ctx.queryParamMap().entrySet()) {
            String key = entry.getKey();
            String decodedParamValue = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);
            Optional<FunctionSpatialization.QueryParams> qp = APIController.enumValueOf(FunctionSpatialization.QueryParams.class, key);

            if (qp.isPresent()) {
                Consumer<String> qpHandler = switch (qp.get()) {
                    case DURATION_IN_SECONDS ->
                        s -> sr.setSeconds(Integer.parseInt(s));
                };
                qpHandler.accept(decodedParamValue);
                
            } else {
                System.out.println("Workflow Cowo endpoint: unknown query param key: " + key);
            }
        }
        return sr;
    }

}
