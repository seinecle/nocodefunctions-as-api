/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodefunctionswebservices.workflow.gaze;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowCoocProps;
import net.clementlevallois.gaze.controller.CoocFunction;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.nocodefunctionswebservices.graphops.RunnableGetTopNodesFromGraph;
import net.clementlevallois.utils.Multiset;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class RunnableCooc {

    private Map<Integer, Multiset<String>> lines = new TreeMap();
    private final WorkflowCoocProps functionProps;
    private final Globals globals;
    private String jobId;
    private String callbackURL;

    public RunnableCooc() {
        this.functionProps = new WorkflowCoocProps(APIController.tempFilesFolder);
        this.globals = new Globals(APIController.tempFilesFolder);
    }

    public void runGazeCoocInBackgroundThread() {
        Runnable runnable = () -> {

            try {

                // --- Step 1 : do the cooc
                CoocFunction coocFunction = new CoocFunction();
                String gexf = coocFunction.createGraphFromCooccurrences(lines, false);

                Path tempResultsPath = functionProps.getGexfFilePath(jobId);
                Files.writeString(tempResultsPath, gexf, StandardCharsets.UTF_8);
                String statusMessage = "function complete";
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
                Files.writeString(tempResultsPath, "cooc job finished", StandardCharsets.UTF_8);
                
                
            } catch (IOException ex) {
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

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public void setCallbackURL(String callbackURL) {
        this.callbackURL = callbackURL;
    }

}
