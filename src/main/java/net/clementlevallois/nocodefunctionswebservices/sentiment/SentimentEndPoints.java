/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.sentiment;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpCode;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import static net.clementlevallois.nocodefunctionswebservices.APIController.increment;
import net.clementlevallois.umigon.classifier.sentiment.ClassifierSentimentOneDocument;
import net.clementlevallois.umigon.controller.UmigonController;
import net.clementlevallois.umigon.explain.controller.UmigonExplain;
import net.clementlevallois.umigon.model.Category;
import net.clementlevallois.umigon.model.Document;
import net.clementlevallois.umigon.model.ResultOneHeuristics;

/**
 *
 * @author LEVALLOIS
 */
public class SentimentEndPoints {

    public static Javalin addAll(Javalin app, UmigonController umigonController) {

        ClassifierSentimentOneDocument classifierOneDocEN = new ClassifierSentimentOneDocument(umigonController.getSemanticsEN());
        ClassifierSentimentOneDocument classifierOneDocFR = new ClassifierSentimentOneDocument(umigonController.getSemanticsFR());

        app.post("/api/sentimentForAText/{lang}", ctx -> {
            String owner = ctx.queryParam("owner");
            if (owner == null || !owner.equals(APIController.pwdOwner)) {
                NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
                increment();
            }
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            TreeMap<Integer, String> lines = new TreeMap();
            TreeMap<Integer, String> results = new TreeMap();
            String body = ctx.body();
            if (body.isEmpty()) {
                objectBuilder.add("-99", "body of the request should not be empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpCode.BAD_REQUEST);
            } else {
                JsonReader jsonReader = Json.createReader(new StringReader(body));
                JsonObject jsonObject = jsonReader.readObject();
                Iterator<String> iteratorKeys = jsonObject.keySet().iterator();
                int i = 0;
                while (iteratorKeys.hasNext()) {
                    lines.put(i++, jsonObject.getString(iteratorKeys.next()));
                }

                String lang = ctx.pathParam("lang");
                ClassifierSentimentOneDocument classifier = null;
                switch (lang) {
                    case "en":
                        classifier = classifierOneDocEN;
                        break;

                    case "fr":
                        classifier = classifierOneDocFR;
                        break;

                    default:
                        objectBuilder.add("-99", "wrong param for lang - lang not supported");
                        JsonObject jsonObjectWrite = objectBuilder.build();
                        ctx.result(jsonObjectWrite.toString()).status(HttpCode.BAD_REQUEST);
                }

                for (Integer key : lines.keySet()) {
                    Document doc = new Document();
                    doc.setText(lines.get(key));
                    doc = classifier.call(doc);
                    results.put(key, UmigonExplain.getSentimentPlainText(doc, "en"));
                }
                ctx.json(results).status(HttpCode.OK);
            }
        });

        app.get("/api/sentimentForAText/text/{lang}", ctx -> {
            String owner = ctx.queryParam("owner");
            if (owner == null || !owner.equals(APIController.pwdOwner)) {
                NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
                increment();
            }
            String text = ctx.queryParam("text");
            if (text == null || text.isBlank()) {
                ctx.result("param \"text\" is missing").status(HttpCode.BAD_REQUEST);
            } else {
                String lang = ctx.pathParam("lang");
                Document doc = new Document();
                doc.setText(text);
                switch (lang) {
                    case "en":
                        doc = classifierOneDocEN.call(doc);
                        break;

                    case "fr":
                        doc = classifierOneDocFR.call(doc);
                        break;

                    default:
                        ctx.result("wrong param for lang - lang not supported").status(HttpCode.BAD_REQUEST);
                }
                String explanationParam = ctx.queryParam("explanation");
                String langExplanation = ctx.queryParam("explanation_lang");
                if (langExplanation == null) {
                    langExplanation = "en";
                }
                if (explanationParam != null && explanationParam.toLowerCase().equals("on")) {
                    String explanationPlainText = UmigonExplain.getExplanationOfHeuristicResultsPlainText(doc, langExplanation);
                    ctx.result(explanationPlainText).status(HttpCode.OK);
                } else {
                    String sentimentPlainText = UmigonExplain.getSentimentPlainText(doc, langExplanation);
                    ctx.result(sentimentPlainText).status(HttpCode.OK);
                }
            }
        });

        app.get("/api/sentimentForAText/json/{lang}", new Handler() {
            @Override
            public void handle(Context ctx) throws Exception {
                JsonObjectBuilder jsonAnswer = Json.createObjectBuilder();
                jsonAnswer.add("info and questions", "admin@clementlevallois.net");
                String owner = ctx.queryParam("owner");
                if (owner == null || !owner.equals(APIController.pwdOwner)) {
                    NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
                    increment();
                }
                String text = ctx.queryParam("text");
                if (text == null || text.isBlank()) {
                    ctx.json("{\"error\":\"query parameter *text* is missing\"}").status(HttpCode.BAD_REQUEST);
                } else {
                    String lang = ctx.pathParam("lang");
                    Document doc = new Document();
                    doc.setText(text);
                    switch (lang) {
                        case "en":
                            doc = classifierOneDocEN.call(doc);
                            break;

                        case "fr":
                            doc = classifierOneDocFR.call(doc);
                            break;

                        default:
                            ctx.json("\"wrong param for lang\":\"lang not supported\"").status(HttpCode.BAD_REQUEST);
                    }
                    String explanationParam = ctx.queryParam("explanation");
                    String langExplanation = ctx.queryParam("explanation_lang");
                    if (langExplanation == null) {
                        langExplanation = "en";
                    }
                    if (explanationParam != null && explanationParam.toLowerCase().equals("on")) {
                        JsonObjectBuilder explanationOfHeuristicResultsJson = UmigonExplain.getExplanationOfHeuristicResultsJson(doc, langExplanation);
                        jsonAnswer.addAll(explanationOfHeuristicResultsJson);
                    } else {
                        jsonAnswer.add("sentiment", UmigonExplain.getSentimentPlainText(doc, langExplanation));
                    }
                    JsonObject jsonToSend = jsonAnswer.build();
                    try ( java.io.StringWriter stringWriter = new StringWriter()) {
                        var jsonWriter = Json.createWriter(stringWriter);
                        jsonWriter.writeObject(jsonToSend);
                        ctx.json(stringWriter.toString()).status(HttpCode.OK);
                    }

                }
            }
        });

        app.get("/api/sentimentForAText/bytes/{lang}", ctx -> {
            Document docInput = new Document();
            increment();
            String text = ctx.queryParam("text");
            String id = ctx.queryParam("id");
            if (id == null) {
                id = UUID.randomUUID().toString().substring(0, 10);
            }
            docInput.setId(id);
            if (text == null) {
                docInput.setText("");
                ctx.result(APIController.byteArraySerializerForDocuments(docInput)).status(HttpCode.BAD_REQUEST);
            } else {
                docInput.setText(text);
                String lang = ctx.pathParam("lang");
                Document docOutput = null;
                switch (lang) {
                    case "en":
                        docOutput = classifierOneDocEN.call(docInput);
                        break;

                    case "fr":
                        docOutput = classifierOneDocFR.call(docInput);
                        break;

                    default:
                        ctx.result(APIController.byteArraySerializerForDocuments(docInput)).status(HttpCode.BAD_REQUEST);
                }
                ctx.result(APIController.byteArraySerializerForDocuments(docOutput)).status(HttpCode.OK);
            }
        }
        );

        return app;

    }
}
