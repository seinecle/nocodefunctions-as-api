package net.clementlevallois.nocodefunctionswebservices.workflow.cowo;

import net.clementlevallois.nocodefunctionswebservices.APIController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.cowo.controller.CowoFunction;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowCowoProps;
import net.clementlevallois.nocodefunctionswebservices.graphops.RunnableGetTopNodesFromGraph;

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
    private String jobId;

    private final WorkflowCowoProps functionProps;
    private final Globals globals;

    public RunnableCowoWorkflow() {
        this.lines = new TreeMap();
        this.userSuppliedStopwords = new HashSet();
        this.functionProps = new WorkflowCowoProps(APIController.tempFilesFolder);
        this.globals = new Globals(APIController.tempFilesFolder);
    }

    @Override
    public void run() {
        boolean overallSuccess = false;
        String statusMessage = "Cowo workflow processing started.";

        LOGGER.log(Level.INFO, "Starting workflow run for id: {0}", jobId);

        try {
            // --- Step 1: Cowo Function ---
            statusMessage = "Starting cowo...";
            APIController.sendProgressUpdate(10, statusMessage, callbackURL, sessionId, jobId);
            CowoFunction cowoFunction = new CowoFunction();
            cowoFunction.setFlattenToAScii(removeAccents);
            cowoFunction.setSessionIdAndCallbackURL(sessionId, callbackURL, jobId);
            String gexf = cowoFunction.analyze(lines, lang, userSuppliedStopwords, minCharNumber, replaceStopwords, isScientificCorpus, firstNames, removeAccents, minCoocFreq, minTermFreq, typeCorrection, maxNGram, lemmatize);
            Path tempResultsPath = functionProps.getGexfFilePath(jobId);
            Files.writeString(tempResultsPath, gexf, StandardCharsets.UTF_8);
            statusMessage = "Cowo function completed";
            APIController.sendProgressUpdate(50, statusMessage, callbackURL, sessionId, jobId);

            // --- Step 2 : detect top nodes and saving the corresponding json files ---
            RunnableGetTopNodesFromGraph getTopNodes = new RunnableGetTopNodesFromGraph(jobId);
            getTopNodes.setGexfAsString(gexf);

            String topNodes = getTopNodes.getTopNodes(30);
            tempResultsPath = globals.getTopNetworkVivaGraphFormattedFilePath(jobId);
            Files.writeString(tempResultsPath, topNodes, StandardCharsets.UTF_8);

            statusMessage = "Cowo workflow completed successfully.";
            overallSuccess = true;
            APIController.sendProgressUpdate(100, statusMessage, callbackURL, sessionId, jobId);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Workflow failed critically for " + jobId, e);
            statusMessage = "Workflow failed: " + e.getMessage();
            overallSuccess = false;
            try {
                APIController.sendProgressUpdate(100, statusMessage, callbackURL, sessionId, jobId);
            } catch (Exception ignored) {
            }
        } finally {
            // --- Step 5: Send Final Callback ---
            APIController.sendProgressUpdate(100, statusMessage, callbackURL, sessionId, jobId);

            // --- Step 6: Cleanup Input Data File ---
            try {
                Path inputPath = functionProps.getOriginalTextInputFilePath(jobId);
                if (Files.deleteIfExists(inputPath)) {
                    LOGGER.log(Level.INFO, "Deleted temporary input file: {0}", inputPath.toString());
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not delete temporary input file: " + jobId, e);
            }

            LOGGER.log(Level.INFO, "Finished workflow run for id: {0} with success={1}", new Object[]{jobId, overallSuccess});
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

    public void setJobId(String jobId) {
        this.jobId = jobId;
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

    public String getJobId() {
        return jobId;
    }

}
