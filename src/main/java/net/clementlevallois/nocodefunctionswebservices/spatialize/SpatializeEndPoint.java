/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.spatialize;

import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.spatialize.controller.SpatializeFunction;

/**
 *
 * @author LEVALLOIS
 */
public class SpatializeEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post("/api/spatialization", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 1, TimeUnit.SECONDS);
            String durationInSeconds = ctx.queryParam("durationInSeconds");
            Integer durationLayout;
            try {
                durationLayout = Integer.valueOf(durationInSeconds);
            } catch (NumberFormatException e) {
                durationLayout = 20;
            }
            byte[] bodyAsBytes = ctx.bodyAsBytes();
            if (bodyAsBytes.length == 0) {
                objectBuilder.add("-99", "body of the request should not be empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                String gexf = new String(bodyAsBytes, StandardCharsets.UTF_8);
                SpatializeFunction spatializer = new SpatializeFunction();
                String gexfSpatialized = spatializer.spatialize(gexf, durationLayout);

                ctx.result(gexfSpatialized.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
            }
        }
        );

        return app;

    }
}
