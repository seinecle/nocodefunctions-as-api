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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.clementlevallois.functions.model.Names;
import net.clementlevallois.graphops.GetTopNodesFromThickestEdges;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class RunnableGetTopNodesFromGraph {

    String gexfAsString;

    private String dataPersistenceId = "1";
    private String sessionId = "1";
    private String callbackURL = "1";

    public void runTopNodesInBackgroundThread(Integer nbTopNodes) {
        Runnable runnable = () -> {
            getTopNodes(nbTopNodes);
        };
        new Thread(runnable).start();

    }

    public String getTopNodes(Integer nbTopNodes) {
        String jsonResult = null;
        try {
            GetTopNodesFromThickestEdges getTopNodes = new GetTopNodesFromThickestEdges(gexfAsString);
            jsonResult = getTopNodes.returnTopNodesAndEdges(nbTopNodes);
            jsonResult = Json.encodePointer(jsonResult);
            String resultFileName = dataPersistenceId + "_" + Names.TOP_NODES + ".json";
            Path tempResultsPath = Path.of(APIController.tempFilesFolder.toString(), resultFileName);
            Files.writeString(tempResultsPath, jsonResult, StandardCharsets.UTF_8);

            JsonObjectBuilder joBuilder = Json.createObjectBuilder();
            joBuilder.add("info", "RESULT_ARRIVED");
            joBuilder.add("function", Names.TOP_NODES.toString());
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
        return jsonResult;

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
