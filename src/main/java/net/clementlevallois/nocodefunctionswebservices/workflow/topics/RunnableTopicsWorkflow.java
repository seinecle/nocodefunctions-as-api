package net.clementlevallois.nocodefunctionswebservices.workflow.topics;

// Imports remain largely the same, remove unused ones like UrlBuilder, JsonWriter etc.
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.topics.topic.detection.function.controller.TopicDetectionFunction;
import net.clementlevallois.utils.Multiset;

import java.io.IOException;
import java.net.ConnectException; // Keep specific exceptions if needed for logging
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates the topics workflow: topic detection, GEXF saving, Json data
 * saving for later excel export.
 */
public class RunnableTopicsWorkflow implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(RunnableTopicsWorkflow.class.getName());

    private TreeMap<Integer, String> lines;
    private Set<String> userSuppliedStopwords;
    private String lang;
    private String sessionId;
    private String callbackURL;
    private int minCharNumber;
    private int minTermFreq;
    private boolean replaceStopwords;
    private boolean removeAccents;
    private boolean isScientificCorpus;
    private boolean lemmatize;
    private String dataPersistenceId;
    private int precision;
    private Set<String> requestedFormats;

    private final HttpClient httpClient;

    public RunnableTopicsWorkflow() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_2)
                .build();
        this.lines = new TreeMap();
        this.userSuppliedStopwords = new HashSet();
    }

    @Override
    public void run() {
        boolean overallSuccess = false;
        boolean jsonStepFailed = false;
        String statusMessage = "Workflow processing started.";
        Path tempDirForThisTask = Path.of(APIController.tempFilesFolder.toString(), dataPersistenceId);

        LOGGER.log(Level.INFO, "Starting workflow run for id: {0}", dataPersistenceId);

        Map<Integer, Multiset<String>> keywordsPerTopicMap = null;
        Map<Integer, Multiset<Integer>> topicsPerLineMap = null;
        String gexfSemanticNetwork = null;

        try {
            // --- Step 1: Topic Detection ---
            statusMessage = "Starting topic detection...";
            sendProgressUpdate(10, statusMessage);
            TopicDetectionFunction topicsFunction = new TopicDetectionFunction();
            topicsFunction.setRemoveAccents(removeAccents);
            topicsFunction.setSessionIdAndCallbackURL(sessionId, callbackURL, dataPersistenceId);
            topicsFunction.analyze(lines, lang, userSuppliedStopwords, replaceStopwords, isScientificCorpus, precision, 4, minCharNumber, minTermFreq, lemmatize);

            keywordsPerTopicMap = topicsFunction.getTopicsNumberToKeyTerms();
            topicsPerLineMap = topicsFunction.getLinesAndTheirKeyTopics();
            gexfSemanticNetwork = topicsFunction.getGexfOfSemanticNetwork();

            if (keywordsPerTopicMap == null || keywordsPerTopicMap.isEmpty()) {
                statusMessage = "Topic detection finished, no topics detected";
                sendProgressUpdate(100, statusMessage);
                return;
            }
            statusMessage = "Topic detection finished.";
            sendProgressUpdate(50, statusMessage);

            // --- Step 2: Save GEXF ---
            if (requestedFormats.contains("gexf")) {
                statusMessage = "Saving GEXF file...";
                sendProgressUpdate(60, statusMessage);
                if (gexfSemanticNetwork != null && !gexfSemanticNetwork.isBlank()) {
                    try {
                        GexfSaverTask gexfTask = new GexfSaverTask(gexfSemanticNetwork, tempDirForThisTask, dataPersistenceId);
                        gexfTask.save();
                        statusMessage = "GEXF file saved.";
                        sendProgressUpdate(70, statusMessage);
                    } catch (IOException e) {
                        statusMessage = "Failed to save GEXF file.";
                        sendProgressUpdate(70, statusMessage);
                    }
                } else {
                    LOGGER.log(Level.WARNING, "GEXF format requested but no GEXF data generated for {0}", dataPersistenceId);
                    statusMessage = "GEXF file could not be generated.";
                    sendProgressUpdate(70, statusMessage);
                }
            }

            // --- Step 3: Generate and save data as Json for the Excel export ---
            if (requestedFormats.contains("excel")) {
                statusMessage = "Generating json file for data export to Excel...";
                sendProgressUpdate(75, statusMessage);
                try {
                    JsonDataSaverTask jsonSavingTask = new JsonDataSaverTask(keywordsPerTopicMap, topicsPerLineMap, gexfSemanticNetwork, tempDirForThisTask, dataPersistenceId
                    );
                    jsonSavingTask.saveJsonData(); // Call the export/save method
                    statusMessage = "Json file saved.";
                    sendProgressUpdate(90, statusMessage);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Json  generation step failed for " + dataPersistenceId, e); // Log context here too
                    statusMessage = "Json generation failed: " + e.getMessage();
                    sendProgressUpdate(90, statusMessage);
                    jsonStepFailed = true;
                }
            }

            // --- Step 4: Finalize Status ---
            if (jsonStepFailed) {
                statusMessage = "Workflow completed with errors (Json generation failed).";
                overallSuccess = true; // Still overall success if GEXF was ok
            } else {
                statusMessage = "Workflow completed successfully.";
                overallSuccess = true;
            }
            sendProgressUpdate(100, statusMessage);

        } catch (Exception e) {
            // Catch major failures
            LOGGER.log(Level.SEVERE, "Workflow failed critically for " + dataPersistenceId, e);
            statusMessage = "Workflow failed: " + e.getMessage();
            overallSuccess = false;
            try {
                sendProgressUpdate(100, statusMessage);
            } catch (Exception ignored) {
            }
        } finally {
            // --- Step 5: Send Final Callback ---
            try {
                sendFinalCallback(overallSuccess, statusMessage);
            } catch (IOException | InterruptedException | URISyntaxException e) {
                LOGGER.log(Level.SEVERE, "Failed to send final callback for " + dataPersistenceId, e);
            }
            // --- Step 6: Cleanup Input Data File ---
            try {
                Path inputPath = tempDirForThisTask.resolve(dataPersistenceId);
                if (Files.deleteIfExists(inputPath)) {
                    LOGGER.log(Level.INFO, "Deleted temporary input file: {0}", inputPath.toString());
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not delete temporary input file: " + dataPersistenceId, e);
            }
            LOGGER.log(Level.INFO, "Finished workflow run for id: {0} with success={1}", new Object[]{dataPersistenceId, overallSuccess});
        }
    }

    /**
     * Sends a progress update callback
     */
    private void sendProgressUpdate(int progress, String message) {
        if (callbackURL == null || callbackURL.isBlank()) {
            return;
        }
        try {
            JsonObjectBuilder joBuilder = Json.createObjectBuilder();
            joBuilder.add("info", "PROGRESS");
            joBuilder.add("function", "topics");
            if (sessionId != null) {
                joBuilder.add("sessionId", sessionId);
            }
            joBuilder.add("dataPersistenceId", dataPersistenceId);
            joBuilder.add("progress", progress);
            joBuilder.add("message", message != null ? message : "");
            sendCallback(joBuilder.build().toString());
        } catch (IOException | InterruptedException | URISyntaxException e) {
            LOGGER.log(Level.WARNING, "Failed to send progress update for " + dataPersistenceId, e);
        }
    }

    /**
     * Sends the final success or failure callback including result paths
     */
    private void sendFinalCallback(boolean success, String message) throws IOException, URISyntaxException, InterruptedException {
        if (callbackURL == null || callbackURL.isBlank()) {
            LOGGER.log(Level.WARNING, "No callback URL configured. Final status cannot be sent for {0}", dataPersistenceId);
            return;
        }
        JsonObjectBuilder joBuilder = Json.createObjectBuilder();
        joBuilder.add("info", success ? "WORKFLOW_COMPLETED" : "FAILED");
        joBuilder.add("function", "topics");
        if (sessionId != null) {
            joBuilder.add("sessionId", sessionId);
        }
        joBuilder.add("dataPersistenceId", dataPersistenceId);
        joBuilder.add("message", message != null ? message : "");
        sendCallback(joBuilder.build().toString());
    }

    /**
     * Generic method to send a JSON payload as a POST request to the callback
     * URL
     */
    private void sendCallback(String jsonPayload) throws IOException, URISyntaxException, InterruptedException {
        URI uri = new URI(callbackURL);
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .POST(bodyPublisher)
                .uri(uri)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .build();

        LOGGER.log(Level.INFO, "Sending callback to {0} for {1}: Payload size={2} chars",
                new Object[]{callbackURL, dataPersistenceId, jsonPayload.length()});

        try {
            HttpResponse<String> resp = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                LOGGER.log(Level.WARNING, "Callback POST failed for {0}. Status: {1}, Response: {2}",
                        new Object[]{dataPersistenceId, resp.statusCode(), resp.body()});
            } else {
                LOGGER.log(Level.INFO, "Callback POST successful for {0}. Status: {1}", new Object[]{dataPersistenceId, resp.statusCode()});
            }
        } catch (HttpTimeoutException e) {
            LOGGER.log(Level.WARNING, "Callback POST timed out for " + dataPersistenceId, e);
            throw new IOException("Callback timed out", e); // Propagate as IOException
        } catch (ConnectException e) {
            LOGGER.log(Level.WARNING, "Callback POST connection refused for " + dataPersistenceId, e);
            throw new IOException("Callback connection refused", e); // Propagate
        }
        // Allow other IOExceptions / InterruptedExceptions to propagate
    }

    public void setUserSuppliedStopwords(Set<String> userSuppliedStopwords) {
        this.userSuppliedStopwords = userSuppliedStopwords;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setCallbackURL(String callbackURL) {
        this.callbackURL = callbackURL;
    }

    public void setMinCharNumber(int minCharNumber) {
        this.minCharNumber = minCharNumber;
    }

    public void setMinTermFreq(int minTermFreq) {
        this.minTermFreq = minTermFreq;
    }

    public void setReplaceStopwords(boolean replaceStopwords) {
        this.replaceStopwords = replaceStopwords;
    }

    public void setRemoveAccents(boolean removeAccents) {
        this.removeAccents = removeAccents;
    }

    public void setIsScientificCorpus(boolean isScientificCorpus) {
        this.isScientificCorpus = isScientificCorpus;
    }

    public void setLemmatize(boolean lemmatize) {
        this.lemmatize = lemmatize;
    }

    public void setDataPersistenceId(String dataPersistenceId) {
        this.dataPersistenceId = dataPersistenceId;
    }

    public void setRequestedFormats(Set<String> requestedFormats) {
        this.requestedFormats = requestedFormats;
    }

    public void setLines(TreeMap<Integer, String> lines) {
        this.lines = lines;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public TreeMap<Integer, String> getLines() {
        return lines;
    }

    public Set<String> getUserSuppliedStopwords() {
        return userSuppliedStopwords;
    }

    public String getDataPersistenceId() {
        return dataPersistenceId;
    }

}
