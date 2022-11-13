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
import java.util.concurrent.TimeUnit;
import net.clementlevallois.gexfvosviewerjson.GexfToVOSViewerJson;
import net.clementlevallois.gexfvosviewerjson.Metadata;
import net.clementlevallois.gexfvosviewerjson.Terminology;
import net.clementlevallois.gexfvosviewerjson.VOSViewerJsonToGexf;

/**
 *
 * @author LEVALLOIS
 */
public class VosViewerConversionEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post("/api/convert2vv", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            byte[] bodyAsBytes = ctx.bodyAsBytes();
            if (bodyAsBytes.length == 0) {
                objectBuilder.add("-99", "body of the request should not be empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                String item = ctx.pathParam("item");
                String items = ctx.pathParam("items");
                String link = ctx.pathParam("link");
                String links = ctx.pathParam("links");
                String linkStrength = ctx.pathParam("linkStrength");
                String totalLinkStrength = ctx.pathParam("totalLinkStrength");
                String descriptionData = ctx.pathParam("descriptionData");

                InputStream isOfTheGexf = new ByteArrayInputStream(bodyAsBytes);
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

                ctx.result(graphAsJsonVosViewer.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
            }
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
                ctx.result(gexfAsString.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
            }
        }
        );

        return app;

    }
}
