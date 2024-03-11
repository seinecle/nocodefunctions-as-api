/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodefunctionswebservices.gaze;

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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.clementlevallois.gaze.controller.SimilarityFunction;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class RunnableGazeSim {

    private Map<String, Set<String>> lines = new TreeMap();
    private String dataPersistenceId = "1";
    private String sessionId = "1";
    private String callbackURL = "1";
    private int minSharedTarget = 1;

    public void runGazeSimInBackgroundThread() {
        Runnable runnable = () -> {

            try {
                SimilarityFunction simFunction = new SimilarityFunction();
                String gexf = simFunction.createSimilarityGraph(lines, minSharedTarget);
                Path tempResultsPath = Path.of(APIController.tempFilesFolder.toString(), dataPersistenceId + "_result");
                Files.writeString(tempResultsPath, gexf, StandardCharsets.UTF_8);

                JsonObjectBuilder joBuilder = Json.createObjectBuilder();
                joBuilder.add("info", "RESULT_ARRIVED");
                joBuilder.add("function", "gaze");
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

                client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException | URISyntaxException ex) {
                Exceptions.printStackTrace(ex);
            }
        };
        new Thread(runnable).start();

    }

    public Map<String, Set<String>> getLines() {
        return lines;
    }

    public void setLines(Map<String, Set<String>> lines) {
        this.lines = lines;
    }

    public String getDataPersistenceId() {
        return dataPersistenceId;
    }

    public void setDataPersistenceId(String dataPersistenceId) {
        this.dataPersistenceId = dataPersistenceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCallbackURL() {
        return callbackURL;
    }

    public void setCallbackURL(String callbackURL) {
        this.callbackURL = callbackURL;
    }

    public int getMinSharedTarget() {
        return minSharedTarget;
    }

    public void setMinSharedTarget(int minSharedTarget) {
        this.minSharedTarget = minSharedTarget;
    }

}
