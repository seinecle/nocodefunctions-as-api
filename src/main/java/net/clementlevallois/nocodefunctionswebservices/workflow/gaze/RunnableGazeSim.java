/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodefunctionswebservices.workflow.gaze;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowGazeProps;
import net.clementlevallois.gaze.controller.SimilarityFunction;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.nocodefunctionswebservices.graphops.RunnableGetTopNodesFromGraph;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class RunnableGazeSim {

    private Map<String, Set<String>> lines = new TreeMap();
    private final WorkflowGazeProps functionProps;
    private final Globals globals;
    private String jobId;
    private String sessionId;
    private String callbackURL;
    private int minSharedTarget = 1;

    public RunnableGazeSim() {
        this.functionProps = new WorkflowGazeProps(APIController.tempFilesFolder);
        this.globals = new Globals(APIController.tempFilesFolder);
    }

    public void runGazeSimInBackgroundThread() {
        Runnable runnable = () -> {

            try {
                // --- Step 1 : do the similarity graph creation
                SimilarityFunction simFunction = new SimilarityFunction();
                String gexf = simFunction.createSimilarityGraph(lines, minSharedTarget);

                Path tempResultsPath = functionProps.getGexfFilePath(jobId);
                Files.writeString(tempResultsPath, gexf, StandardCharsets.UTF_8);
                String statusMessage = "similarity computation function complete";
                APIController.sendProgressUpdate(80, statusMessage, callbackURL, sessionId, jobId);

                // --- Step 2 : detect top nodes and saving the corresponding json files ---
                RunnableGetTopNodesFromGraph getTopNodes = new RunnableGetTopNodesFromGraph();
                getTopNodes.setDataPersistenceId(jobId);
                getTopNodes.setGexfAsString(gexf);

                String topNodes = getTopNodes.getTopNodes(30);
                tempResultsPath = globals.getTopNetworkVivaGraphFormattedFilePath(jobId);
                Files.writeString(tempResultsPath, topNodes, StandardCharsets.UTF_8);

                statusMessage = "Top graph identification completed successfully.";
                APIController.sendProgressUpdate(100, statusMessage, callbackURL, sessionId, jobId);

            } catch (IOException ex) {
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

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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