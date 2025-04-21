/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.vvconversion;

import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import static java.util.stream.Collectors.toList;
import net.clementlevallois.gexfvosviewerjson.GexfToVOSViewerJson;
import net.clementlevallois.gexfvosviewerjson.Metadata;
import net.clementlevallois.gexfvosviewerjson.Terminology;
import net.clementlevallois.gexfvosviewerjson.VOSViewerJsonToGexf;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.utils.UnicodeBOMInputStream;
import org.openide.util.Exceptions;

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
                ctx.result("error for conversion to vv, gexf file not found on disk".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_BAD_REQUEST);
            }
            GexfToVOSViewerJson converter = new GexfToVOSViewerJson(gexfAsString);
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
            if (graphAsJsonVosViewer.isBlank()) {
                ctx.result("error for vv conversion, json file not produced".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_INTERNAL_ERROR);
            } else {
                graphAsJsonVosViewer = Json.encodePointer(graphAsJsonVosViewer);
                ctx.result(graphAsJsonVosViewer.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
            }
        }
        );

        app.get("/api/convert2gexf", ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            String dataPersistenceUniqueId = Optional.ofNullable(ctx.queryParam("dataPersistenceUniqueId")).orElse("none");
            Path tempDataPath = Path.of(APIController.tempFilesFolder.toString(), dataPersistenceUniqueId);
            if (!Files.exists(tempDataPath)) {
                ctx.result("error for conversion to gexf, vv json file not found on disk".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                try {
                    FileInputStream fis = new FileInputStream(tempDataPath.toString());
                    UnicodeBOMInputStream ubis = new UnicodeBOMInputStream(fis);
                    InputStreamReader isr = new InputStreamReader(ubis);
                    List<String> lines;
                    try (BufferedReader br = new BufferedReader(isr)) {
                        ubis.skipBOM();
                        lines = br.lines().collect(toList());
                    }
                    if (lines == null) {
                        ctx.result("error reading vv json file for conversion to gexf".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_INTERNAL_ERROR);
                    } else {
                        String jsonFixed = String.join("\n", lines);
                        VOSViewerJsonToGexf converter = new VOSViewerJsonToGexf(jsonFixed);
                        String gexfAsString = converter.convertToGexf();
                        if (gexfAsString == null || gexfAsString.isBlank()) {
                            ctx.result("error in the conversion if vv json to gexf".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_INTERNAL_ERROR);
                        } else {
                            ctx.result(gexfAsString.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
                        }
                    }
                } catch (NullPointerException | IOException ex) {
                    Exceptions.printStackTrace(ex);
                    ctx.result("error reading vv json file for conversion to gexf".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_INTERNAL_ERROR);
                }
            }

        }
        );
        return app;
    }
}
