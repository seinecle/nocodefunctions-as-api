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
import java.util.TreeMap;
import net.clementlevallois.gaze.controller.CoocFunction;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.utils.Multiset;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class RunnableGazeCooc {

    private Map<Integer, Multiset<String>> lines = new TreeMap();
    private String dataPersistenceId = "1";
    private String sessionId = "1";
    private String callbackURL = "1";

    public void runGazeCoocInBackgroundThread() {
        Runnable runnable = () -> {

            try {
                CoocFunction coocFunction = new CoocFunction();
                String gexf = coocFunction.createGraphFromCooccurrences(lines, false);

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

    public Map<Integer, Multiset<String>> getLines() {
        return lines;
    }

    public void setLines(Map<Integer, Multiset<String>> lines) {
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

}
