/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodefunctionswebservices.graphops;

import jakarta.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.graphops.GetTopNodesFromThickestEdges;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class RunnableGetTopNodesFromGraph {

    private String gexfAsString;
    private final String jobId;
    private final Globals globals;

    public RunnableGetTopNodesFromGraph(String jobId) {
        this.jobId = jobId;
        this.globals = new Globals(APIController.tempFilesFolder);
    }

    public void runTopNodesInBackgroundThread(Integer nbTopNodes) {
        Runnable runnable = () -> {
            getTopNodes(nbTopNodes);
        };
        new Thread(runnable).start();
    }

    public String getTopNodes(Integer nbTopNodes) {
        String jsonResult = null;
        try {
            GetTopNodesFromThickestEdges getTopNodes = new GetTopNodesFromThickestEdges(gexfAsString);
            jsonResult = getTopNodes.returnTopNodesAndEdges(nbTopNodes);
            jsonResult = Json.encodePointer(jsonResult);
            Path tempResultsPath = globals.getTopNetworkVivaGraphFormattedFilePath(jobId);
            Files.writeString(tempResultsPath, jsonResult, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return jsonResult;

    }

    public void setGexfAsString(String gexfAsString) {
        this.gexfAsString = gexfAsString;
    }

}
