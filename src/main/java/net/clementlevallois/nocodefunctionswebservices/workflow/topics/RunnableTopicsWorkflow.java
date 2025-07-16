package net.clementlevallois.nocodefunctionswebservices.workflow.topics;

import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.topics.topic.detection.function.controller.TopicDetectionFunction;
import net.clementlevallois.utils.Multiset;

import java.io.IOException;
import java.net.http.HttpClient;
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
    private final Globals globals;

    public RunnableTopicsWorkflow() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_2)
                .build();
        this.lines = new TreeMap();
        this.userSuppliedStopwords = new HashSet();
        this.globals = new Globals(APIController.tempFilesFolder);
    }

    @Override
    public void run() {
        boolean overallSuccess = false;
        boolean jsonStepFailed = false;
        String statusMessage = "Starting topic detection...";
        this.props = new WorkflowTopicsProps(APIController.tempFilesFolder);

        LOGGER.log(Level.INFO, "Starting workflow run for id: {0}", jobId);

        try {
            // --- Step 1: Topic Detection ---
            APIController.sendProgressUpdate(10, statusMessage, callbackURL, jobId);

            TopicDetectionFunction topicsFunction = new TopicDetectionFunction();
            topicsFunction.setRemoveAccents(removeAccents);
            topicsFunction.setSessionIdAndCallbackURL(callbackURL, jobId);
            topicsFunction.analyze(lines, lang, userSuppliedStopwords, replaceStopwords, isScientificCorpus, precision, 4, minCharNumber, minTermFreq, lemmatize);

            final Map<Integer, Multiset<String>> keywordsPerTopicMap = topicsFunction.getTopicsNumberToKeyTerms();
            final Map<Integer, Multiset<Integer>> topicsPerLineMap = topicsFunction.getLinesAndTheirKeyTopics();
            final String gexfSemanticNetwork = topicsFunction.getGexfOfSemanticNetwork();

            if (keywordsPerTopicMap == null || keywordsPerTopicMap.isEmpty()) {
                statusMessage = "Topic detection finished, no topics detected.";
                APIController.sendProgressUpdate(100, statusMessage, callbackURL, jobId);
                return;
            }
            statusMessage = "Topic detection finished.";
            APIController.sendProgressUpdate(50, statusMessage, callbackURL, jobId);

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
            APIController.sendProgressUpdate(100, statusMessage, callbackURL, jobId);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Workflow failed critically for " + jobId, e);
            statusMessage = "Workflow failed: " + e.getMessage();
            overallSuccess = false;
            try {
                APIController.sendProgressUpdate(100, statusMessage, callbackURL, jobId);
            } catch (Exception ignored) {
            }
        } finally {
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
                APIController.sendProgressUpdate(70, "GEXF file saved.", callbackURL, jobId);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        } else {
            APIController.sendProgressUpdate(70, "GEXF file could not be generated.", callbackURL, jobId);
        }
    }

    private void saveJsonFile(Map<Integer, Multiset<String>> keywordsPerTopicMap, Map<Integer, Multiset<Integer>> topicsPerLineMap, Path resultFilePath) {
        try {
            JsonDataSaverTask jsonSavingTask = new JsonDataSaverTask(keywordsPerTopicMap, topicsPerLineMap, resultFilePath);
            jsonSavingTask.saveJsonData();
            APIController.sendProgressUpdate(70, "Json file saved.", callbackURL, jobId);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            APIController.sendProgressUpdate(70, "Json file could not be saved.", callbackURL, jobId);
        }
    }

    public void setUserSuppliedStopwords(Set<String> userSuppliedStopwords) {
        this.userSuppliedStopwords = userSuppliedStopwords;
    }

    public void setLang(String lang) {
        this.lang = lang;
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
