package net.clementlevallois.nocodefunctionswebservices.workflow.communityinsights;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import net.clementlevallois.nocodefunctionswebservices.APIController;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.StructuredTaskScope;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowCommunityInsightsProps;
import net.clementlevallois.functions.model.KeyNodesInfo;
import net.clementlevallois.nocodefunctionswebservices.graphops.RunnableGetKeyNodes;
import net.clementlevallois.nocodefunctionswebservices.graphops.RunnableGetTextPerCommunity;
import net.clementlevallois.nocodefunctionswebservices.llms.RunnableContextFromSample;

public class RunnableCommunityInsightsWorkflow implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(RunnableCommunityInsightsWorkflow.class.getName());

    private String gexf;
    private String sourceLang;
    private String targetLang;
    private String sessionId;
    private String callbackURL;
    private String jobId;
    private String textualAttribute;
    private String userSuppliedCommunityFieldName;
    private int maxTopNodesPerCommunityAsInteger;
    private int minCommunitySizeAsInteger;
    private int maxTextLength = 1000;

    private final HttpClient httpClient;

    public RunnableCommunityInsightsWorkflow() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    @Override
    public void run() {
        Path tempDirForThisTask = Path.of(APIController.tempFilesFolder.toString(), jobId);

        ConcurrentMap<String, String> contextPerCommunity = new ConcurrentHashMap();

        LOGGER.log(Level.INFO, "Starting workflow run for id: {0}", jobId);

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            if (!textualAttribute.isBlank()) {
                scope.fork(() -> {
                    // --- Task 1: Text collection per community ---
                    String statusMessage = "Starting text detection per community";
                    sendProgressUpdate(10, statusMessage);

                    var textOps = new RunnableGetTextPerCommunity();
                    textOps.setGexfAsString(gexf);
                    textOps.setCallbackURL(callbackURL);
                    textOps.setDataPersistenceId(jobId);
                    textOps.setSessionId(sessionId);
                    Map<String, String> results = textOps.getTextPerCommunity(userSuppliedCommunityFieldName, textualAttribute, maxTopNodesPerCommunityAsInteger, minCommunitySizeAsInteger, maxTextLength);

                    statusMessage = "text detection per community: over";
                    sendProgressUpdate(20, statusMessage);

                    // --- Task 2: Context per community ---
                    statusMessage = "Starting context identification per community";
                    sendProgressUpdate(20, statusMessage);

                    for (Map.Entry<String, String> entry : results.entrySet()) {
                        scope.fork(() -> {
                            var contextDetector = new RunnableContextFromSample();
                            contextDetector.setCallbackURL(callbackURL);
                            contextDetector.setDataPersistenceId(jobId);
                            contextDetector.setSessionId(sessionId);
                            String contextFromSample = contextDetector.getContextFromSample(gexf, sourceLang, targetLang);
                            contextPerCommunity.put(entry.getKey(), contextFromSample);
                            return null;
                        });
                    }

                    JsonObjectBuilder joBuilder = Json.createObjectBuilder();
                    for (Map.Entry<String, String> entry : contextPerCommunity.entrySet()) {
                        joBuilder.add(entry.getKey(), entry.getValue());
                    }
                    String contextSampleFile = jobId + WorkflowCommunityInsightsProps.CONTEXT_FROM_SAMPLE_FILE_NAME_EXTENSION + WorkflowCommunityInsightsProps.CONTEXT_FROM_SAMPLE_FILE_EXTENSION;
                    Path tempResultsPath = Path.of(APIController.tempFilesFolder.toString(), contextSampleFile);
                    Files.writeString(tempResultsPath, joBuilder.build().toString(), StandardCharsets.UTF_8);

                    statusMessage = "context identification per community: over";
                    sendProgressUpdate(50, statusMessage);
                    return null;
                });
            }
            scope.fork(() -> {

                // --- Task 3: Key nodes insights ---
                String statusMessage = "Starting key nodes insights";
                sendProgressUpdate(40, statusMessage);

                var keyNodes = new RunnableGetKeyNodes();
                keyNodes.setGexfAsString(gexf);
                keyNodes.setCallbackURL(callbackURL);
                keyNodes.setDataPersistenceId(jobId);
                keyNodes.setSessionId(sessionId);
                KeyNodesInfo keyNodesInfo = keyNodes.getKeyNodes(userSuppliedCommunityFieldName, maxTopNodesPerCommunityAsInteger, minCommunitySizeAsInteger);

                String keyNodesFile = jobId + WorkflowCommunityInsightsProps.KEY_NODES_NAME_EXTENSION + WorkflowCommunityInsightsProps.KEY_NODES_FILE_EXTENSION;
                Path tempResultsPath = Path.of(APIController.tempFilesFolder.toString(), keyNodesFile);
                Files.writeString(tempResultsPath, keyNodesInfo.toJsonForInsights().toString(), StandardCharsets.UTF_8);
                statusMessage = "key nodes per community: over";
                sendProgressUpdate(50, statusMessage);
                return null;
            });

            // --- Step 4: Finalize Status ---
            scope.join();
            String statusMessage = "Workflow completed successfully.";
            sendProgressUpdate(100, statusMessage);
            String workflowCompleteFlagFile = jobId + Globals.WORKFLOW_COMPLETE_FILE_NAME_EXTENSION;
            Path tempResultsPath = Path.of(APIController.tempFilesFolder.toString(), workflowCompleteFlagFile);
            Files.writeString(tempResultsPath, "community insights workflow is complete", StandardCharsets.UTF_8);
            scope.throwIfFailed();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Workflow failed critically for " + jobId, e);
            String statusMessage = "Workflow failed: " + e.getMessage();
            try {
                sendProgressUpdate(100, statusMessage);
            } catch (Exception ignored) {
            }
        } finally {
            // Cleanup Input Data File ---
            try {
                Path inputPath = tempDirForThisTask.resolve(jobId);
                if (Files.deleteIfExists(inputPath)) {
                    LOGGER.log(Level.INFO, "Deleted temporary input file: {0}", inputPath.toString());
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not delete temporary input file: " + jobId, e);
            }
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

        this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

    }

    public void setSourceLang(String sourceLang) {
        this.sourceLang = sourceLang;
    }

    public void setTargetLang(String targetLang) {
        this.targetLang = targetLang;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setCallbackURL(String callbackURL) {
        this.callbackURL = callbackURL;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public void setUserSuppliedCommunityFieldName(String userSuppliedCommunityFieldName) {
        this.userSuppliedCommunityFieldName = userSuppliedCommunityFieldName;
    }

    public void setGexf(String gexf) {
        this.gexf = gexf;
    }

    public void setMaxTopNodesPerCommunityAsInteger(int maxTopNodesPerCommunityAsInteger) {
        this.maxTopNodesPerCommunityAsInteger = maxTopNodesPerCommunityAsInteger;
    }

    public void setMinCommunitySizeAsInteger(int minCommunitySizeAsInteger) {
        this.minCommunitySizeAsInteger = minCommunitySizeAsInteger;
    }

    public void setTextualAttribute(String textualAttribute) {
        this.textualAttribute = textualAttribute;
    }

    public void setMaxTextLength(int maxTextLength) {
        this.maxTextLength = maxTextLength;
    }

}
