/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.vvconversion;

import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.gexfvosviewerjson.GexfToVOSViewerJson;
import net.clementlevallois.gexfvosviewerjson.Metadata;
import net.clementlevallois.gexfvosviewerjson.Terminology;
import net.clementlevallois.gexfvosviewerjson.VOSViewerJsonToGexf;
import net.clementlevallois.nocodefunctionswebservices.APIController;

/**
 *
 * @author LEVALLOIS
 */
public class VosViewerConversionEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.get("/api/convert2vv", ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            String item = Optional.ofNullable(ctx.queryParam("item")).orElse("item");
            String items = Optional.ofNullable(ctx.queryParam("items")).orElse("items");
            String link = Optional.ofNullable(ctx.queryParam("link")).orElse("link");
            String links = Optional.ofNullable(ctx.queryParam("links")).orElse("links");
            String linkStrength = Optional.ofNullable(ctx.queryParam("linkStrength")).orElse("link strength");
            String totalLinkStrength = Optional.ofNullable(ctx.queryParam("totalLinkStrength")).orElse("total link strength");
            String descriptionData = Optional.ofNullable(ctx.queryParam("descriptionData")).orElse("description");
            String dataPersistenceUniqueId = Optional.ofNullable(ctx.queryParam("dataPersistenceUniqueId")).orElse("none");
            Path tempDataPath = Path.of(APIController.tempFilesFolder.toString(), dataPersistenceUniqueId + "_result");
            String gexfAsString = "";
            if (Files.exists(tempDataPath)) {
                gexfAsString = Files.readString(tempDataPath, StandardCharsets.UTF_8);
            } else {
                ctx.result("error for vv conversion, gexf file not found in disk".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_BAD_REQUEST);
            }

            InputStream isOfTheGexf = new ByteArrayInputStream(gexfAsString.getBytes(StandardCharsets.UTF_8));
            GexfToVOSViewerJson converter = new GexfToVOSViewerJson(isOfTheGexf);
            converter.setMaxNumberNodes(500);
            converter.setTerminologyData(new Terminology());
            converter.getTerminologyData().setItem(item);
            converter.getTerminologyData().setItems(items);
            converter.getTerminologyData().setLink(link);
            converter.getTerminologyData().setLinks(links);
            converter.getTerminologyData().setLink_strength(linkStrength);
            converter.getTerminologyData().setTotal_link_strength(totalLinkStrength);
            converter.setMetadataData(new Metadata());
            converter.getMetadataData().setAuthorCanBePlural("");
            converter.getMetadataData().setDescriptionOfData(descriptionData);
            String graphAsJsonVosViewer = converter.convertToJson();
            graphAsJsonVosViewer = Json.encodePointer(graphAsJsonVosViewer);

            ctx.result(graphAsJsonVosViewer.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
        }
        );

        app.post("/api/convert2gexf", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            byte[] bodyAsBytes = ctx.bodyAsBytes();
            if (bodyAsBytes.length == 0) {
                objectBuilder.add("-99", "body of the request should not be empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                InputStream isOfTheJson = new ByteArrayInputStream(bodyAsBytes);
                VOSViewerJsonToGexf converter = new VOSViewerJsonToGexf(isOfTheJson);
                String gexfAsString = converter.convertToGexf();
                if (gexfAsString == null) {
                    ctx.result("error in the conversion to gexf".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_INTERNAL_ERROR);
                } else {
                    ctx.result(gexfAsString.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
                }
            }
        }
        );

        return app;

    }
}
