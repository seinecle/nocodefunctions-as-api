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
import net.clementlevallois.functions.model.WorkflowSimProps;
import net.clementlevallois.gaze.controller.SimilarityFunction;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.nocodefunctionswebservices.graphops.RunnableGetTopNodesFromGraph;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class RunnableGazeSim {

    private Map<String, Set<String>> sourcesAndTargets = new TreeMap();
    private final WorkflowSimProps functionProps;
    private final Globals globals;
    private String jobId;
    private String callbackURL;
    private int minSharedTarget = 1;
    private int sourceColIndex;

    public RunnableGazeSim() {
        this.functionProps = new WorkflowSimProps(APIController.tempFilesFolder);
        this.globals = new Globals(APIController.tempFilesFolder);
    }

    public void runGazeSimInBackgroundThread() {
        Runnable runnable = () -> {

            try {
                // --- Step 1 : do the similarity graph creation
                SimilarityFunction simFunction = new SimilarityFunction();
                String gexf = simFunction.createSimilarityGraph(sourcesAndTargets, minSharedTarget);

                Path tempResultsPath = functionProps.getGexfFilePath(jobId);
                Files.writeString(tempResultsPath, gexf, StandardCharsets.UTF_8);
                String statusMessage = "similarity computation function complete";
                APIController.sendProgressUpdate(80, statusMessage, callbackURL, jobId);

                // --- Step 2 : detect top nodes and saving the corresponding json files ---
                RunnableGetTopNodesFromGraph getTopNodes = new RunnableGetTopNodesFromGraph(jobId);
                getTopNodes.setGexfAsString(gexf);

                String topNodes = getTopNodes.getTopNodes(30);
                tempResultsPath = globals.getTopNetworkVivaGraphFormattedFilePath(jobId);
                Files.writeString(tempResultsPath, topNodes, StandardCharsets.UTF_8);

                statusMessage = "Top graph identification completed successfully.";
                APIController.sendProgressUpdate(100, statusMessage, callbackURL, jobId);

                // --- Step 3 : writing a job complete file signal flag ---
                tempResultsPath = globals.getWorkflowCompleteFilePath(jobId);
                Files.writeString(tempResultsPath, "sim job finished", StandardCharsets.UTF_8);

            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        };
        new Thread(runnable).start();

    }

    public Map<String, Set<String>> getSourcesAndTargets() {
        return sourcesAndTargets;
    }

    public void setSourcesAndTargets(Map<String, Set<String>> sourcesAndTargets) {
        this.sourcesAndTargets = sourcesAndTargets;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
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

    public int getSourceColIndex() {
        return sourceColIndex;
    }

    public void setSourceColIndex(int sourceColIndex) {
        this.sourceColIndex = sourceColIndex;
    }

}
