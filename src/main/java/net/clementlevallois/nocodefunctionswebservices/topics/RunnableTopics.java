/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodefunctionswebservices.topics;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.topics.topic.detection.function.controller.TopicDetectionFunction;
import net.clementlevallois.utils.Multiset;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class RunnableTopics {

    private TreeMap<Integer, String> lines = new TreeMap();
    private Set<String> userSuppliedStopwords = new HashSet();
    private String lang = "en";
    private String typeCorrection = "none";
    private String sessionId = "";
    private String callbackURL = "";
    private int minCharNumber = 4;
    private int minCoocFreq = 3;
    private int minTermFreq = 3;
    private int maxNGram = 4;
    private boolean replaceStopwords = false;
    private boolean removeAccents = false;
    private boolean isScientificCorpus = false;
    private boolean lemmatize = true;
    private String dataPersistenceId = "";
    private int precision = 1;

    public void runTopicsInBackgroundThread() {
        Runnable runnable = () -> {
            try {
                TopicDetectionFunction topicsFunction = new TopicDetectionFunction();
                topicsFunction.setRemoveAccents(removeAccents);
                topicsFunction.setSessionIdAndCallbackURL(sessionId, callbackURL, dataPersistenceId);
                topicsFunction.analyze(lines, lang, userSuppliedStopwords, replaceStopwords, isScientificCorpus, precision, 4, minCharNumber, minTermFreq, lemmatize);
                Map<Integer, Multiset<String>> topics = topicsFunction.getTopicsNumberToKeyTerms();
                Map<Integer, Multiset<Integer>> linesAndKeyTopics = topicsFunction.getLinesAndTheirKeyTopics();
                String gexfOfSemanticNetwork = topicsFunction.getGexfOfSemanticNetwork();

                Set<Map.Entry<Integer, Multiset<String>>> entrySetTopicsToKeyTerms = topics.entrySet();
                Set<Map.Entry<Integer, Multiset<Integer>>> entrySetLinesToKeyTopics = linesAndKeyTopics.entrySet();

                JsonObjectBuilder globalResults = Json.createObjectBuilder();
                JsonObjectBuilder topicsPerLine = Json.createObjectBuilder();
                JsonObjectBuilder keywordsPerTopic = Json.createObjectBuilder();

                for (Map.Entry<Integer, Multiset<String>> entry : entrySetTopicsToKeyTerms) {
                    String communityName = String.valueOf((entry.getKey()));
                    Multiset<String> values = entry.getValue();
                    JsonObjectBuilder termsAndTheirCountsInOneCOmmunity = Json.createObjectBuilder();
                    for (String element : values.getElementSet()) {
                        termsAndTheirCountsInOneCOmmunity.add(element, values.getCount(element));
                    }
                    keywordsPerTopic.add(communityName, termsAndTheirCountsInOneCOmmunity);
                }
                for (Map.Entry<Integer, Multiset<Integer>> entry : entrySetLinesToKeyTopics) {
                    String lineNumber = String.valueOf((entry.getKey()));
                    Multiset<Integer> values = entry.getValue();
                    JsonObjectBuilder topicsAndTheirCountsForOneLine = Json.createObjectBuilder();
                    for (Integer element : values.getElementSet()) {
                        topicsAndTheirCountsForOneLine.add(String.valueOf(element), values.getCount(element));
                    }
                    topicsPerLine.add(lineNumber, topicsAndTheirCountsForOneLine);
                }

                globalResults.add("keywordsPerTopic", keywordsPerTopic);
                globalResults.add("topicsPerLine", topicsPerLine);
                globalResults.add("gexf", gexfOfSemanticNetwork);
                Path tempResultsPath = Path.of(APIController.tempFilesFolder.toString(), dataPersistenceId + "_result");
                Files.writeString(tempResultsPath, globalResults.build().toString(), StandardCharsets.UTF_8);

                JsonObjectBuilder joBuilder = Json.createObjectBuilder();
                joBuilder.add("info", "RESULT_ARRIVED");
                joBuilder.add("function", "topics");
                joBuilder.add("sessionId", sessionId);
                joBuilder.add("dataPersistenceId", dataPersistenceId);
                String joStringPayload = joBuilder.build().toString();
                HttpClient client = HttpClient.newHttpClient();
                URI uri = new URI(callbackURL);

                HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(joStringPayload);

                HttpRequest request = HttpRequest.newBuilder()
                        .POST(bodyPublisher)
                        .header("Content-Type", "application/json")
                        .uri(uri)
                        .build();

                HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

            } catch (IOException | URISyntaxException | InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            }
        };
        new Thread(runnable).start();
    }

    public TreeMap<Integer, String> getLines() {
        return lines;
    }

    public void setLines(TreeMap<Integer, String> lines) {
        this.lines = lines;
    }

    public Set<String> getUserSuppliedStopwords() {
        return userSuppliedStopwords;
    }

    public void setUserSuppliedStopwords(Set<String> userSuppliedStopwords) {
        this.userSuppliedStopwords = userSuppliedStopwords;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getTypeCorrection() {
        return typeCorrection;
    }

    public void setTypeCorrection(String typeCorrection) {
        this.typeCorrection = typeCorrection;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCallbackURL() {
        return callbackURL;
    }

    public void setCallbackURL(String callbackURL) {
        this.callbackURL = callbackURL;
    }

    public int getMinCharNumber() {
        return minCharNumber;
    }

    public void setMinCharNumber(int minCharNumber) {
        this.minCharNumber = minCharNumber;
    }

    public int getMinCoocFreq() {
        return minCoocFreq;
    }

    public void setMinCoocFreq(int minCoocFreq) {
        this.minCoocFreq = minCoocFreq;
    }

    public int getMinTermFreq() {
        return minTermFreq;
    }

    public void setMinTermFreq(int minTermFreq) {
        this.minTermFreq = minTermFreq;
    }

    public int getMaxNGram() {
        return maxNGram;
    }

    public void setMaxNGram(int maxNGram) {
        this.maxNGram = maxNGram;
    }

    public boolean isReplaceStopwords() {
        return replaceStopwords;
    }

    public void setReplaceStopwords(boolean replaceStopwords) {
        this.replaceStopwords = replaceStopwords;
    }

    public boolean isRemoveAccents() {
        return removeAccents;
    }

    public void setRemoveAccents(boolean removeAccents) {
        this.removeAccents = removeAccents;
    }

    public boolean isIsScientificCorpus() {
        return isScientificCorpus;
    }

    public void setIsScientificCorpus(boolean isScientificCorpus) {
        this.isScientificCorpus = isScientificCorpus;
    }

    public boolean isLemmatize() {
        return lemmatize;
    }

    public void setLemmatize(boolean lemmatize) {
        this.lemmatize = lemmatize;
    }

    public String getDataPersistenceId() {
        return dataPersistenceId;
    }

    public void setDataPersistenceId(String dataPersistenceId) {
        this.dataPersistenceId = dataPersistenceId;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

}
