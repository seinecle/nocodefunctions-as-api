/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodefunctionswebservices.graphops;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.clementlevallois.functions.model.KeyNodesInfo;
import net.clementlevallois.functions.model.Names;
import net.clementlevallois.graphops.KeyNodeInsights;
import net.clementlevallois.graphops.TextSampleFromCommunities;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class RunnableGetTextPerCommunity {

    String gexfAsString;

    private String dataPersistenceId = "1";
    private String sessionId = "1";
    private String callbackURL = "1";

    public void runTextPerCommunityInBackgroundThread(String userSuppliedCommunityFieldName, String textualAttribute, Integer maxTopNodesPerCommunity, Integer minCommunitySize, Integer maxLengthText) {
        Runnable runnable = () -> {
            getTextPerCommunity(userSuppliedCommunityFieldName, textualAttribute, maxTopNodesPerCommunity, minCommunitySize, maxLengthText);
        };
        new Thread(runnable).start();

    }

    public Map<String, String> getTextPerCommunity(String userSuppliedCommunityFieldName, String textualAttribute, Integer maxTopNodesPerCommunity, Integer minCommunitySize, Integer maxLengthText) {
        Map<String, String> textPerCommunity = null;
        try {
            var textOps = new TextSampleFromCommunities();
            textOps.importGexfAsGraph(gexfAsString);
            textPerCommunity = textOps.analyze(userSuppliedCommunityFieldName, textualAttribute, maxTopNodesPerCommunity, minCommunitySize, maxLengthText);
            byte[] bytes = APIController.byteArraySerializerForAnyObject(textPerCommunity);
            String resultFileName = dataPersistenceId + "_" + Names.TEXT_PER_COMMUNITY + ".txt";
            Path tempResultsPath = Path.of(APIController.tempFilesFolder.toString(), resultFileName);
            Files.write(tempResultsPath, bytes);

            JsonObjectBuilder joBuilder = Json.createObjectBuilder();
            joBuilder.add("info", "RESULT_ARRIVED");
            joBuilder.add("function", Names.TEXT_PER_COMMUNITY.toString());
            joBuilder.add("sessionId", sessionId);
            joBuilder.add("dataPersistenceId", dataPersistenceId);
            String joStringPayload = joBuilder.build().toString();
            HttpClient client = HttpClient.newHttpClient();
            URI uri = new URI(callbackURL);

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(joStringPayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .header("Content-Type", "application/json")
                    .uri(uri)
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | URISyntaxException ex) {
            Exceptions.printStackTrace(ex);
        }
        return textPerCommunity;

    }

    public void setDataPersistenceId(String dataPersistenceId) {
        this.dataPersistenceId = dataPersistenceId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setCallbackURL(String callbackURL) {
        this.callbackURL = callbackURL;
    }

    public void setGexfAsString(String gexfAsString) {
        this.gexfAsString = gexfAsString;
    }

}
