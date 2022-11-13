/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.bibd;

import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.functions.model.bibd.BIBDResults;
import net.clementlevallois.gexfvosviewerjson.GexfToVOSViewerJson;
import net.clementlevallois.gexfvosviewerjson.Metadata;
import net.clementlevallois.gexfvosviewerjson.Terminology;
import net.clementlevallois.gexfvosviewerjson.VOSViewerJsonToGexf;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.testbibd.BIBDCustom;

/**
 *
 * @author LEVALLOIS
 */
public class BIBDEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post("/api/bibd/getblocks", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            byte[] bodyAsBytes = ctx.bodyAsBytes();
            if (bodyAsBytes.length == 0) {
                objectBuilder.add("-99", "body of the request should not be empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                Integer nbItems = Integer.valueOf(ctx.pathParam("nbItems"));
                Integer nbBlocks = Integer.valueOf(ctx.pathParam("nbBlocks"));
                Integer nbAnnotators = Integer.valueOf(ctx.pathParam("nbAnnotators"));
                Integer nbAppearances = Integer.valueOf(ctx.pathParam("nbAppearances"));
                Integer blockSize = Integer.valueOf(ctx.pathParam("blockSize"));
                Integer maxSamePairs = Integer.valueOf(ctx.pathParam("maxSamePairs"));
                List<String> items = new ArrayList();

                String body = new String(bodyAsBytes, StandardCharsets.UTF_8);
                if (body.isEmpty()) {
                    objectBuilder.add("-99", "body of the request should not be empty");
                    JsonObject jsonObject = objectBuilder.build();
                    ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
                } else {
                    JsonReader jsonReader = Json.createReader(new StringReader(body));
                    JsonObject jsonObject = jsonReader.readObject();
                    for (String nextKey : jsonObject.keySet()) {
                        items.add(jsonObject.getString(nextKey));
                    }
                }
                BIBDCustom bibdMaker = new BIBDCustom();
                BIBDResults blocksAndMetadata = bibdMaker.getBlocksAndMetadata(items, nbItems, nbBlocks, nbAnnotators, nbAppearances, blockSize, maxSamePairs);
                byte[] bytesArrayToReturn = APIController.byteArraySerializerForAnyObject(blocksAndMetadata);
                ctx.result(bytesArrayToReturn).status(HttpURLConnection.HTTP_OK);
            }
        }
        );

        return app;

    }
}
