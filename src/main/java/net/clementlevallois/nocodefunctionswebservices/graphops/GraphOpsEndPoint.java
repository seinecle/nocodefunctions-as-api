/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.graphops;

import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.graphops.GetTopNodesFromThickestEdges;
import net.clementlevallois.nocodefunctionswebservices.APIController;

/**
 *
 * @author LEVALLOIS
 */
public class GraphOpsEndPoint {

    public static Javalin addAll(Javalin app) {

        app.get("/api/graphops/topnodes", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            String dataPersistenceId = ctx.queryParam("dataPersistenceId");
            Path tempDataPath = Path.of(APIController.tempFilesFolder.toString(), dataPersistenceId + "_result");
            String gexfAsString = "";
            int i = 0;
            while (!Files.exists(tempDataPath) || !Files.isReadable(tempDataPath)) {
                try {
                    Thread.sleep(200); // Wait for 200 ms before retrying
                    i++;

                    if (i > 500) {
                        objectBuilder.add("-97", "time out, gexf file not found on disk");
                        objectBuilder.add("gexf file: ", tempDataPath.toString());
                        JsonObject jsonObject = objectBuilder.build();
                        ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Interrupted, exiting...");
                    objectBuilder.add("-98", "gexf file not found on disk");
                    objectBuilder.add("gexf file: ", tempDataPath.toString());
                    JsonObject jsonObject = objectBuilder.build();
                    ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
                }
            }
            try {
                gexfAsString = Files.readString(tempDataPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                objectBuilder.add("-99", "gexf file not readable on disk");
                objectBuilder.add("gexf file: ", tempDataPath.toString());
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
            }
            String nbNodes = ctx.queryParam("nbNodes");
            Integer nbNodesAsInteger;
            try {
                nbNodesAsInteger = Integer.valueOf(nbNodes);
            } catch (NumberFormatException e) {
                nbNodesAsInteger = 30;
            }
            GetTopNodesFromThickestEdges getTopNodes = new GetTopNodesFromThickestEdges(gexfAsString);
            String jsonResult = getTopNodes.returnTopNodesAndEdges(nbNodesAsInteger);
            jsonResult = Json.encodePointer(jsonResult);
            if (jsonResult == null || jsonResult.isBlank()) {
                ctx.result("error in graph ops API, return json is null or empty".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_INTERNAL_ERROR);
            } else {
                ctx.result(jsonResult.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
            }
        });
        return app;

    }
}
