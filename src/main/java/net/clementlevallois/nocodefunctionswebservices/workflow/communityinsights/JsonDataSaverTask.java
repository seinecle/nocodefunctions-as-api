package net.clementlevallois.nocodefunctionswebservices.workflow.communityinsights;

import net.clementlevallois.nocodefunctionswebservices.workflow.topics.*;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriter;
import net.clementlevallois.utils.Multiset;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles constructing the result JSON object and saving it to a file.
 * This JSON data can later be used by the frontend/backend to generate an Excel file.
 */
public class JsonDataSaverTask {

    private static final Logger LOGGER = Logger.getLogger(JsonDataSaverTask.class.getName());

    private final Map<Integer, Multiset<String>> keywordsPerTopicMap;
    private final Map<Integer, Multiset<Integer>> topicsPerLineMap;
    private final String gexfContent; // Include GEXF in the JSON output
    private final Path targetDirectory;
    private final String dataUniqueId; // Usually dataPersistenceId

    /**
     * Constructor for the JSON data saving task.
     *
     * @param keywordsPerTopicMap Map of keywords per topic.
     * @param topicsPerLineMap    Map of topics per line.
     * @param gexfContent         The generated GEXF string.
     * @param targetDirectory     The directory where the JSON file should be saved.
     * @param baseFilename        The base name for the file (e.g., dataPersistenceId), "_result.json" will be appended.
     */
    public JsonDataSaverTask(Map<Integer, Multiset<String>> keywordsPerTopicMap,
                             Map<Integer, Multiset<Integer>> topicsPerLineMap,
                             String gexfContent,
                             Path targetDirectory,
                             String baseFilename) {
        this.keywordsPerTopicMap = Objects.requireNonNull(keywordsPerTopicMap, "Keywords map cannot be null");
        this.topicsPerLineMap = Objects.requireNonNull(topicsPerLineMap, "Topics per line map cannot be null");
        this.gexfContent = gexfContent; // Can be null/empty if GEXF wasn't generated/requested
        this.targetDirectory = Objects.requireNonNull(targetDirectory, "Target directory cannot be null");
        this.dataUniqueId = Objects.requireNonNull(baseFilename, "Base filename cannot be null");
    }

    /**
     * Constructs the result JSON and saves it to the specified file.
     *
     * @return The absolute path of the saved JSON file as a String.
     * @throws IOException If an error occurs during JSON construction or file writing.
     */
    public String saveJsonData() throws IOException {
        Path jsonPath = targetDirectory.resolve(dataUniqueId + "_result.json");
        LOGGER.log(Level.INFO, "Attempting to save result JSON to: {0}", jsonPath.toAbsolutePath());

        // Build the JSON Object using the helper method
        JsonObject jsonPayload = buildResultJsonObject(this.keywordsPerTopicMap, this.topicsPerLineMap, this.gexfContent);

        // Write the JSON object to a String
        StringWriter sw = new StringWriter();
        try (JsonWriter jw = Json.createWriter(sw)) {
             jw.write(jsonPayload);
        } 
        String jsonString = sw.toString();

        if (jsonString.isBlank()) {
             LOGGER.log(Level.WARNING, "Constructed JSON string is blank, cannot save file for {0}", dataUniqueId);
             throw new IOException("Cannot save blank JSON result data.");
        }

        // Write the JSON String to the file
        try {
            Files.writeString(jsonPath, jsonString, StandardCharsets.UTF_8);
            LOGGER.log(Level.INFO, "Successfully saved result JSON to: {0}", jsonPath.toAbsolutePath());
            return jsonPath.toAbsolutePath().toString();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to write result JSON file: " + jsonPath.toAbsolutePath(), e);
            throw e; // Re-throw
        }
    }

    /**
     * Helper to build the JSON object containing results
     */
     private JsonObject buildResultJsonObject(Map<Integer, Multiset<String>> keywordsPerTopicMap,
                                              Map<Integer, Multiset<Integer>> topicsPerLineMap,
                                              String gexfSemanticNetwork) {
         JsonObjectBuilder globalResults = Json.createObjectBuilder();
         JsonObjectBuilder keywordsBuilder = Json.createObjectBuilder();
         JsonObjectBuilder topicsBuilder = Json.createObjectBuilder();

         // Build keywordsPerTopic
         if (keywordsPerTopicMap != null) {
             for (Map.Entry<Integer, Multiset<String>> entry : keywordsPerTopicMap.entrySet()) {
                 JsonObjectBuilder termsBuilder = Json.createObjectBuilder();
                 if (entry.getValue() != null) {
                     for (String element : entry.getValue().getElementSet()) {
                         termsBuilder.add(element, entry.getValue().getCount(element));
                     }
                 }
                 keywordsBuilder.add(String.valueOf(entry.getKey()), termsBuilder);
             }
         }

        // Build topicsPerLine
         if (topicsPerLineMap != null) {
             for (Map.Entry<Integer, Multiset<Integer>> entry : topicsPerLineMap.entrySet()) {
                 JsonObjectBuilder countsBuilder = Json.createObjectBuilder();
                 if (entry.getValue() != null) {
                     for (Integer element : entry.getValue().getElementSet()) {
                         countsBuilder.add(String.valueOf(element), entry.getValue().getCount(element));
                     }
                 }
                 topicsBuilder.add(String.valueOf(entry.getKey()), countsBuilder);
             }
         }

         globalResults.add("keywordsPerTopic", keywordsBuilder);
         globalResults.add("topicsPerLine", topicsBuilder);
         // Include GEXF directly in the JSON if it was generated
         globalResults.add("gexf", gexfSemanticNetwork != null ? gexfSemanticNetwork : "");

         return globalResults.build();
     }
}