package net.clementlevallois.nocodefunctionswebservices.workflow.communityinsights;

import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowCommunityInsightsProps;
import net.clementlevallois.nocodefunctionswebservices.APIController;

public class CommunityInsightsEndPoint {

    public static Javalin addAll(Javalin app) {
        app.post(Globals.API_ENDPOINT_ROOT + WorkflowCommunityInsightsProps.ENDPOINT, ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            String body = ctx.body();
            if (body.isBlank()) {
                ctx.status(HttpURLConnection.HTTP_BAD_REQUEST)
                        .result(Json.createObjectBuilder()
                                .add("-99", "community insights endpoint: body of the request should not be empty")
                                .build()
                                .toString());
                return;
            }

            RunnableCommunityInsightsWorkflow workflow = parseRequest(body);
            workflow.setGexf(body);
            workflow.run();

            ctx.status(HttpURLConnection.HTTP_OK).result("ok");
        });

        return app;
    }

    private static RunnableCommunityInsightsWorkflow parseRequest(String body) throws Exception {
        RunnableCommunityInsightsWorkflow workflow = new RunnableCommunityInsightsWorkflow();
        JsonReader reader = Json.createReader(new StringReader(body));
        JsonObject json = reader.readObject();

        for (var entry : json.entrySet()) {
            String key = entry.getKey();
            switch (key) {
                case "dataPersistenceId" -> {
                    handleDataPersistence(workflow, json.getString(key));
                    workflow.setJobId(json.getString(key));
                }
                case "sourceLang" ->
                    workflow.setSourceLang(json.getString(key, "en"));
                case "targetLang" ->
                    workflow.setTargetLang(json.getString(key, "en"));
                case "maxTopNodesPerCommunity" ->
                    workflow.setMaxTopNodesPerCommunityAsInteger(json.getInt(key, 5));
                case "minCommunitySize" ->
                    workflow.setMinCommunitySizeAsInteger(json.getInt(key, 10));
                case "userSuppliedCommunityFieldName" ->
                    workflow.setUserSuppliedCommunityFieldName(json.getString(key, ""));
                case "textualAttribute" ->
                    workflow.setTextualAttribute(json.getString(key, ""));
                case "sessionId" ->
                    workflow.setSessionId(json.getString(key));
                case "callbackURL" ->
                    workflow.setCallbackURL(json.getString(key));
                default -> {
                    System.out.println("json key received in topics workflow api endpoint not recognized");
                }
            }
        }
        return workflow;
    }

    private static void handleDataPersistence(RunnableCommunityInsightsWorkflow workflow, String dataPersistenceId) throws Exception {
        workflow.setJobId(dataPersistenceId);
        Path inputFile = APIController.tempFilesFolder.resolve(dataPersistenceId).resolve(dataPersistenceId);
        if (Files.exists(inputFile) && !Files.isDirectory(inputFile)) {
            String gexf = Files.readString(inputFile, StandardCharsets.UTF_8);
            workflow.setGexf(gexf);
            Files.deleteIfExists(inputFile);
        } else {
            System.out.println("gexf file not found");
        }
    }

}
