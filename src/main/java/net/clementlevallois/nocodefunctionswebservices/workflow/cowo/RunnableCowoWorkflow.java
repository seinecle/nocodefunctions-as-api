package net.clementlevallois.nocodefunctionswebservices.workflow.cowo;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import net.clementlevallois.nocodefunctionswebservices.APIController;

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
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.cowo.controller.CowoFunction;
import net.clementlevallois.functions.model.CommonExpressions;
import net.clementlevallois.functions.model.WorkflowCowoProperties;
import net.clementlevallois.nocodefunctionswebservices.graphops.RunnableGetTopNodesFromGraph;
import org.openide.util.Exceptions;

/**
 * Orchestrates the topics workflow: topic detection, GEXF saving, Json data
 * saving for later excel export.
 */
public class RunnableCowoWorkflow implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(RunnableCowoWorkflow.class.getName());

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
    private boolean firstNames;
    private String typeCorrection;
    private int maxNGram;
    private int minCoocFreq;
    private boolean lemmatize;
    private String dataPersistenceId;

    private final HttpClient httpClient;
    private final WorkflowCowoProperties functionProps;

    public RunnableCowoWorkflow() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_2)
                .build();
        this.lines = new TreeMap();
        this.userSuppliedStopwords = new HashSet();
        this.functionProps = new WorkflowCowoProperties(APIController.tempFilesFolder);
    }

    @Override
    public void run() {
        boolean overallSuccess = false;
        String statusMessage = "Cowo workflow processing started.";

        LOGGER.log(Level.INFO, "Starting workflow run for id: {0}", dataPersistenceId);

        try {
            // --- Step 1: Cowo Function ---
            statusMessage = "Starting cowo...";
            sendProgressUpdate(10, statusMessage);
            CowoFunction cowoFunction = new CowoFunction();
            cowoFunction.setFlattenToAScii(removeAccents);
            cowoFunction.setSessionIdAndCallbackURL(sessionId, callbackURL, dataPersistenceId);
            String gexf = cowoFunction.analyze(lines, lang, userSuppliedStopwords, minCharNumber, replaceStopwords, isScientificCorpus, firstNames, removeAccents, minCoocFreq, minTermFreq, typeCorrection, maxNGram, lemmatize);
            Path tempResultsPath = functionProps.getGexfFilePath(dataPersistenceId);
            Files.writeString(tempResultsPath, gexf, StandardCharsets.UTF_8);
            statusMessage = "Cowo function completed";
            sendProgressUpdate(50, statusMessage);

            // --- Step 2 : detect top nodes and saving the corresponding json files ---
            RunnableGetTopNodesFromGraph getTopNodes = new RunnableGetTopNodesFromGraph();
            getTopNodes.setCallbackURL(callbackURL);
            getTopNodes.setDataPersistenceId(dataPersistenceId);
            getTopNodes.setGexfAsString(gexf);
            getTopNodes.setSessionId(sessionId);

            String topNodes = getTopNodes.getTopNodes(30);
            tempResultsPath = functionProps.getTopNetworkFilePath(dataPersistenceId);
            Files.writeString(tempResultsPath, topNodes, StandardCharsets.UTF_8);

            statusMessage = "Cowo workflow completed successfully.";
            overallSuccess = true;
            sendProgressUpdate(100, statusMessage);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Workflow failed critically for " + dataPersistenceId, e);
            statusMessage = "Workflow failed: " + e.getMessage();
            overallSuccess = false;
            try {
                sendProgressUpdate(100, statusMessage);
            } catch (Exception ignored) {
            }
        } finally {
            // --- Step 5: Send Final Callback ---
            sendFinalCallback(overallSuccess, statusMessage);

            // --- Step 6: Cleanup Input Data File ---
            try {
                Path inputPath = functionProps.getOriginalTextInputFilePath(dataPersistenceId);
                if (Files.deleteIfExists(inputPath)) {
                    LOGGER.log(Level.INFO, "Deleted temporary input file: {0}", inputPath.toString());
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not delete temporary input file: " + dataPersistenceId, e);
            }

            LOGGER.log(Level.INFO, "Finished workflow run for id: {0} with success={1}", new Object[]{dataPersistenceId, overallSuccess});
        }
    }

    private void sendProgressUpdate(int progress, String message) {
        if (callbackURL == null || callbackURL.isBlank()) {
            return;
        }
        JsonObjectBuilder joBuilder = Json.createObjectBuilder();
        joBuilder.add("info", "PROGRESS");
        joBuilder.add("function", WorkflowCowoProperties.NAME);
        if (sessionId != null) {
            joBuilder.add("sessionId", sessionId);
        }
        joBuilder.add("dataPersistenceId", dataPersistenceId);
        joBuilder.add("progress", progress);
        joBuilder.add("message", message != null ? message : "");
        sendCallback(joBuilder.build().toString());
    }

    /**
     * Sends the final success or failure callback including result paths
     */
    private void sendFinalCallback(boolean success, String message) {
        if (callbackURL == null || callbackURL.isBlank()) {
            LOGGER.log(Level.WARNING, "No callback URL configured. Final status cannot be sent for {0}", dataPersistenceId);
            return;
        }
        JsonObjectBuilder joBuilder = Json.createObjectBuilder();
        joBuilder.add("info", success ? "WORKFLOW_COMPLETED" : "FAILED");
        joBuilder.add("function", WorkflowCowoProperties.NAME);
        if (sessionId != null) {
            joBuilder.add("sessionId", sessionId);
        }
        joBuilder.add("dataPersistenceId", dataPersistenceId);
        joBuilder.add("progress", 100);
        joBuilder.add("message", message != null ? message : "");
        sendCallback(joBuilder.build().toString());
    }

    /**
     * Generic method to send a JSON payload as a POST request to the callback
     * URL
     */
    private void sendCallback(String jsonPayload) {
        try {
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
            } catch (ConnectException e) {
                LOGGER.log(Level.WARNING, "Callback POST connection refused for " + dataPersistenceId, e);
            } catch (IOException | InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            }
        } catch (URISyntaxException ex) {
            Exceptions.printStackTrace(ex);
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

    public void setTypeCorrection(String typeCorrection) {
        this.typeCorrection = typeCorrection;
    }

    public void setMaxNGram(int maxNGram) {
        this.maxNGram = maxNGram;
    }

    public void setMinCoocFreq(int minCoocFreq) {
        this.minCoocFreq = minCoocFreq;
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

    public void setLines(TreeMap<Integer, String> lines) {
        this.lines = lines;
    }

    public void setFirstNames(boolean firstNames) {
        this.firstNames = firstNames;
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
