package net.clementlevallois.nocodefunctionswebservices.workflow.topics;


import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.topics.topic.detection.function.controller.TopicDetectionFunction;
import net.clementlevallois.utils.Multiset;

import java.io.IOException;
import java.net.ConnectException;
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
import java.util.concurrent.StructuredTaskScope;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowTopicsProps;
import org.openide.util.Exceptions;


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
    private String jobId;
    private int precision;
    private Set<String> requestedFormats;

    private final HttpClient httpClient;
    private WorkflowTopicsProps props;
    private Globals globals;

    public RunnableTopicsWorkflow() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_2)
                .build();
        this.lines = new TreeMap();
        this.userSuppliedStopwords = new HashSet();
        this.globals = new Globals (APIController.tempFilesFolder);
    }

    @Override
    public void run() {
        boolean overallSuccess = false;
        boolean jsonStepFailed = false;
        String statusMessage = "Workflow processing started.";
        this.props = new WorkflowTopicsProps(APIController.tempFilesFolder);

        LOGGER.log(Level.INFO, "Starting workflow run for id: {0}", jobId);

        try {
            // --- Step 1: Topic Detection ---
            statusMessage = "Starting topic detection...";
            sendProgressUpdate(10, statusMessage);

            TopicDetectionFunction topicsFunction = new TopicDetectionFunction();
            topicsFunction.setRemoveAccents(removeAccents);
            topicsFunction.setSessionIdAndCallbackURL(sessionId, callbackURL, jobId);
            topicsFunction.analyze(lines, lang, userSuppliedStopwords, replaceStopwords, isScientificCorpus, precision, 4, minCharNumber, minTermFreq, lemmatize);

            final Map<Integer, Multiset<String>> keywordsPerTopicMap = topicsFunction.getTopicsNumberToKeyTerms();
            final Map<Integer, Multiset<Integer>> topicsPerLineMap = topicsFunction.getLinesAndTheirKeyTopics();
            final String gexfSemanticNetwork = topicsFunction.getGexfOfSemanticNetwork();

            if (keywordsPerTopicMap == null || keywordsPerTopicMap.isEmpty()) {
                statusMessage = "Topic detection finished, no topics detected.";
                sendProgressUpdate(100, statusMessage);
                return;
            }
            statusMessage = "Topic detection finished.";
            sendProgressUpdate(50, statusMessage);

            // --- Step 2 and Step 3: Save GEXF and Json concurrently ---
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

                if (requestedFormats.contains("gexf")) {
                    scope.fork(() -> {
                        saveGexfFile(gexfSemanticNetwork, props.getGexfFilePath(jobId));
                        return null;
                    });
                }

                if (requestedFormats.contains("json")) {
                    scope.fork(() -> {
                        saveJsonFile(keywordsPerTopicMap, topicsPerLineMap, props.getGlobalResultsJsonFilePath(jobId));
                        return null;
                    });
                }

                scope.join();
                scope.throwIfFailed();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during saving files in workflow for " + jobId, e);
                jsonStepFailed = true;
            }

            // --- Step 4: Finalize Status ---
            if (jsonStepFailed) {
                statusMessage = "Workflow completed with errors (Json generation failed).";
                overallSuccess = false;
            } else {
                statusMessage = "Workflow completed successfully.";
                overallSuccess = true;
            }
            sendProgressUpdate(100, statusMessage);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Workflow failed critically for " + jobId, e);
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
                Files.writeString(globals.getWorkflowCompleteFilePath(jobId), "topics job finished");
            } catch (IOException | InterruptedException | URISyntaxException e) {
                LOGGER.log(Level.SEVERE, "Failed to send final callback for " + jobId, e);
            }

            // --- Step 6: Cleanup Input Data File ---
            try {
                if (Files.deleteIfExists(props.getOriginalTextInputFilePath(jobId))) {
                    LOGGER.log(Level.INFO, "Deleted temporary input file");
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not delete temporary input file: " + jobId, e);
            }

            LOGGER.log(Level.INFO, "Finished workflow run for id: {0} with success={1}", new Object[]{jobId, overallSuccess});
        }
    }

    private void saveGexfFile(String gexfSemanticNetwork, Path pathFileToSave) {
        if (gexfSemanticNetwork != null && !gexfSemanticNetwork.isBlank()) {
            try {
                Files.writeString(pathFileToSave, gexfSemanticNetwork);
                sendProgressUpdate(70, "GEXF file saved.");
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        } else {
            sendProgressUpdate(70, "GEXF file could not be generated.");
        }
    }

    private void saveJsonFile(Map<Integer, Multiset<String>> keywordsPerTopicMap, Map<Integer, Multiset<Integer>> topicsPerLineMap,Path resultFilePath) {
        try {
            JsonDataSaverTask jsonSavingTask = new JsonDataSaverTask(keywordsPerTopicMap, topicsPerLineMap, resultFilePath);
            jsonSavingTask.saveJsonData();
            sendProgressUpdate(90, "Json file saved.");
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

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
            joBuilder.add("dataPersistenceId", jobId);
            joBuilder.add("progress", progress);
            joBuilder.add("message", message != null ? message : "");
            sendCallback(joBuilder.build().toString());
        } catch (IOException | InterruptedException | URISyntaxException e) {
            LOGGER.log(Level.WARNING, "Failed to send progress update for " + jobId, e);
        }
    }

    /**
     * Sends the final success or failure callback including result paths
     */
    private void sendFinalCallback(boolean success, String message) throws IOException, URISyntaxException, InterruptedException {
        if (callbackURL == null || callbackURL.isBlank()) {
            LOGGER.log(Level.WARNING, "No callback URL configured. Final status cannot be sent for {0}", jobId);
            return;
        }
        JsonObjectBuilder joBuilder = Json.createObjectBuilder();
        joBuilder.add("info", success ? "WORKFLOW_COMPLETED" : "FAILED");
        joBuilder.add("function", "topics");
        if (sessionId != null) {
            joBuilder.add("sessionId", sessionId);
        }
        joBuilder.add("dataPersistenceId", jobId);
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
                new Object[]{callbackURL, jobId, jsonPayload.length()});

        try {
            HttpResponse<String> resp = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                LOGGER.log(Level.WARNING, "Callback POST failed for {0}. Status: {1}, Response: {2}",
                        new Object[]{jobId, resp.statusCode(), resp.body()});
            } else {
                LOGGER.log(Level.INFO, "Callback POST successful for {0}. Status: {1}", new Object[]{jobId, resp.statusCode()});
            }
        } catch (HttpTimeoutException e) {
            LOGGER.log(Level.WARNING, "Callback POST timed out for " + jobId, e);
            throw new IOException("Callback timed out", e); // Propagate as IOException
        } catch (ConnectException e) {
            LOGGER.log(Level.WARNING, "Callback POST connection refused for " + jobId, e);
            throw new IOException("Callback connection refused", e); // Propagate
        }
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

    public void setJobId(String jobId) {
        this.jobId = jobId;
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

    public String getJobId() {
        return jobId;
    }

}
