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
import net.clementlevallois.functions.model.Names;
import net.clementlevallois.graphops.GetTopNodesFromThickestEdges;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class RunnableGetTopNodesFromGraph {

    String gexfAsString;

    private String dataPersistenceId = "1";

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
            String resultFileName = GlobalsdataPersistenceId + "_" + Names.TOP_NODES + ".json";
            Path tempResultsPath = Path.of(APIController.tempFilesFolder.toString(), resultFileName);
            Files.writeString(tempResultsPath, jsonResult, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return jsonResult;

    }

    public void setDataPersistenceId(String dataPersistenceId) {
        this.dataPersistenceId = dataPersistenceId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setCallbackURL(String callbackURL) {
        this.callbackURL = callbackURL;
    }

    public void setGexfAsString(String gexfAsString) {
        this.gexfAsString = gexfAsString;
    }

}
