/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.linkprediction;

import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.linkprediction.controller.LinkPredictionController;

/**
 *
 * @author LEVALLOIS
 */
public class LinkPredictionEndPoint {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post("/api/linkprediction", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            byte[] bodyAsBytes = ctx.bodyAsBytes();
            if (bodyAsBytes.length == 0) {
                objectBuilder.add("-99", "body of the request should not be empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                int nbPredictions = Objects.requireNonNullElse(Integer.valueOf(ctx.queryParam("nb_predictions")), 1);

                InputStream isOfTheGexf = new ByteArrayInputStream(bodyAsBytes);
                LinkPredictionController predictor = new LinkPredictionController();
                String uniqueId = UUID.randomUUID().toString().substring(0, 10);
                String gexfAugmented = predictor.runPrediction(isOfTheGexf, nbPredictions, uniqueId);
                List<LinkPredictionController.LinkPredictionProbability> topPredictions = predictor.getTopPredictions();
                JsonObjectBuilder predictionsBuilder = Json.createObjectBuilder();
                int counterPredictions = 1;
                for (LinkPredictionController.LinkPredictionProbability link : topPredictions) {
                    JsonObjectBuilder onePrediction = Json.createObjectBuilder();
                    onePrediction.add("source node id", (String) link.getNodeSource().getId());
                    onePrediction.add("source node label", link.getNodeSource().getLabel());
                    onePrediction.add("source node degree", 3);
                    onePrediction.add("target node id", (String) link.getNodeTarget().getId());
                    onePrediction.add("target node label", link.getNodeTarget().getLabel());
                    onePrediction.add("target node degree", 3);
                    onePrediction.add("prediction value", link.getPredictionValue());
                    predictionsBuilder.add(String.valueOf(counterPredictions++), onePrediction);
                }
                objectBuilder.add("predictions", predictionsBuilder);
                objectBuilder.add("gexf augmented", gexfAugmented);

                JsonObject jsonObjectResult = objectBuilder.build();
                ctx.result(jsonObjectResult.toString().getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
            }
        });

        return app;

    }
}
