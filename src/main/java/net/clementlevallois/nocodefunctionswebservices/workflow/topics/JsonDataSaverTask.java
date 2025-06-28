package net.clementlevallois.nocodefunctionswebservices.workflow.topics;

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
    private final Path targetFilePath;

    public JsonDataSaverTask(Map<Integer, Multiset<String>> keywordsPerTopicMap,
                             Map<Integer, Multiset<Integer>> topicsPerLineMap,
                             Path targetDirectory) {
        this.keywordsPerTopicMap = Objects.requireNonNull(keywordsPerTopicMap, "Keywords map cannot be null");
        this.topicsPerLineMap = Objects.requireNonNull(topicsPerLineMap, "Topics per line map cannot be null");
        this.targetFilePath = Objects.requireNonNull(targetDirectory, "Target directory cannot be null");
    }
    public String saveJsonData() throws IOException {
        LOGGER.log(Level.INFO, "Attempting to save result JSON to: {0}", targetFilePath.toAbsolutePath());

        // Build the JSON Object using the helper method
        JsonObject jsonPayload = buildResultJsonObject(this.keywordsPerTopicMap, this.topicsPerLineMap);

        // Write the JSON object to a String
        StringWriter sw = new StringWriter();
        try (JsonWriter jw = Json.createWriter(sw)) {
             jw.write(jsonPayload);
        } 
        String jsonString = sw.toString();

        if (jsonString.isBlank()) {
             LOGGER.log(Level.WARNING, "Constructed JSON string is blank, cannot save file");
        }

        try {
            Files.writeString(targetFilePath, jsonString, StandardCharsets.UTF_8);
            LOGGER.log(Level.INFO, "Successfully saved result JSON to: {0}", targetFilePath.toAbsolutePath());
            return targetFilePath.toAbsolutePath().toString();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to write result JSON file: " + targetFilePath.toAbsolutePath(), e);
            throw e; // Re-throw
        }
    }

    /**
     * Helper to build the JSON object containing results
     */
     private JsonObject buildResultJsonObject(Map<Integer, Multiset<String>> keywordsPerTopicMap,
                                              Map<Integer, Multiset<Integer>> topicsPerLineMap) {
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

         return globalResults.build();
     }
}