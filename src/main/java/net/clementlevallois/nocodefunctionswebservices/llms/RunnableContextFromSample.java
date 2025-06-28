/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodefunctionswebservices.llms;

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
import net.clementlevallois.functions.model.Globals.Names;
import net.clementlevallois.llm.functions.LLMsOps;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class RunnableContextFromSample {

    private String dataPersistenceId = "1";
    private String sessionId = "1";
    private String callbackURL = "1";

    public void runContextFromSampleInBackgroundThread(String rawText, String sourceLanguage, String targetLanguage) {
        Runnable runnable = () -> {
            getContextFromSample(rawText, sourceLanguage, targetLanguage);
        };
        new Thread(runnable).start();

    }

    public String getContextFromSample(String rawText, String sourceLanguage, String targetLanguage) {
        String context = "";
        try {
            LLMsOps llmOps = new LLMsOps();
            context = llmOps.getDomainContextFromText(rawText, sourceLanguage, targetLanguage);
            String resultFileName = dataPersistenceId + "_" + Names.CONTEXT_FROM_SAMPLE.getDescription() + ".txt";
            Path tempResultsPath = Path.of(APIController.tempFilesFolder.toString(), resultFileName);
            Files.writeString(tempResultsPath, context, StandardCharsets.UTF_8);

            JsonObjectBuilder joBuilder = Json.createObjectBuilder();
            joBuilder.add("info", "RESULT_ARRIVED");
            joBuilder.add("function", Names.CONTEXT_FROM_SAMPLE.toString());
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
        return context;

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
}
