/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.organic;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import static net.clementlevallois.nocodefunctionswebservices.APIController.increment;
import net.clementlevallois.umigon.classifier.controller.UmigonController;
import net.clementlevallois.umigon.classifier.organic.ClassifierSentimentOneDocumentOrganic;
import net.clementlevallois.umigon.explain.controller.UmigonExplain;
import net.clementlevallois.umigon.explain.parameters.HtmlSettings;
import net.clementlevallois.umigon.model.Document;

/**
 *
 * @author LEVALLOIS
 */
public class OrganicEndPoints {

    public static Javalin addAll(Javalin app, UmigonController umigonController) {

        ClassifierSentimentOneDocumentOrganic classifierOneDocEN = new ClassifierSentimentOneDocumentOrganic(umigonController.getSemanticsEN());
        ClassifierSentimentOneDocumentOrganic classifierOneDocFR = new ClassifierSentimentOneDocumentOrganic(umigonController.getSemanticsFR());

        app.post("/api/organicForAText/{lang}", ctx -> {
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
                ctx.result(jsonObject.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
            } else {
                JsonReader jsonReader = Json.createReader(new StringReader(body));
                JsonObject jsonObject = jsonReader.readObject();
                Iterator<String> iteratorKeys = jsonObject.keySet().iterator();
                int i = 0;
                while (iteratorKeys.hasNext()) {
                    lines.put(i++, jsonObject.getString(iteratorKeys.next()));
                }

                String lang = ctx.pathParam("lang");
                ClassifierSentimentOneDocumentOrganic classifier = null;
                switch (lang) {
                    case "en":
                        classifier = classifierOneDocEN;
                        break;

                    case "fr":
                        classifier = classifierOneDocFR;
                        break;

                    default: {
                        objectBuilder.add("-99", "wrong param for lang - lang not supported");
                        JsonObject jsonObjectWrite = objectBuilder.build();
                        ctx.result(jsonObjectWrite.toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
                        break;
                    }
                }

                for (Integer key : lines.keySet()) {
                    Document doc = new Document();
                    doc.setText(lines.get(key));
                    doc = classifier.call(doc);
                    results.put(key, UmigonExplain.getOrganicPlainText(doc, "en"));
                }
                ctx.json(results).status(HttpURLConnection.HTTP_OK);
            }
        });

        app.get("/api/organicForAText", ctx -> {
            JsonObjectBuilder jsonAnswer = Json.createObjectBuilder();

            String owner = ctx.queryParam("owner");
            if (owner == null || !owner.equals(APIController.pwdOwner)) {
                NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
                increment();
            }
            String text = ctx.queryParam("text");
            String withoutContactAndTextTitle = ctx.queryParam("shorter");
            Boolean withoutContactAndTextTitleBoolean;
            if (withoutContactAndTextTitle != null && (withoutContactAndTextTitle.equals("true") | withoutContactAndTextTitle.equals("false")) && Boolean.parseBoolean(withoutContactAndTextTitle)) {
                withoutContactAndTextTitleBoolean = true;
            } else {
                withoutContactAndTextTitleBoolean = false;
            }

            String textLang = ctx.queryParam("text-lang");
            if (textLang == null || (textLang.isBlank())) {
                textLang = "en";
            }
            if (text == null || text.isBlank()) {
                text = "";
            }
            String outputFormat = ctx.queryParam("output-format");
            if (outputFormat == null) {
                outputFormat = "plain";
            }
            Document doc = new Document();
            doc.setText(text.trim());
            String id = ctx.queryParam("id");
            if (id == null) {
                id = UUID.randomUUID().toString().substring(0, 10);
            }
            doc.setId(id);

            switch (textLang.trim()) {
                case "en":
                    doc = classifierOneDocEN.call(doc);
                    break;
                case "fr":
                    doc = classifierOneDocFR.call(doc);
                    break;
            }
            String explanationParam = ctx.queryParam("explanation");
            String langExplanation = ctx.queryParam("explanation-lang");
            if (langExplanation == null || langExplanation.isBlank()) {
                langExplanation = "en";
            }
            doc = UmigonExplain.enrichDocWithPlainTextOrganicResults(doc, langExplanation.trim());

            String contactPointPlain = "Made with https://nocodefunctions.com. Remarks, questions, corrections: admin@clementlevallois.net";

            switch (outputFormat.trim()) {
                case "plain":
                {
                    if (explanationParam != null && explanationParam.trim().toLowerCase().equals("on")) {
                        String explanationInPlain = UmigonExplain.getExplanationOfHeuristicOrganicResultsPlainText(doc, langExplanation.trim());
                        if (!doc.getDecisions().isEmpty()) {
                            explanationInPlain = explanationInPlain + " " + UmigonExplain.getExplanationsOfDecisionsPlainText(doc, langExplanation.trim());
                        }
                        String resultString = "*** " + contactPointPlain + " ***" + "\n\n" + explanationInPlain;
                        ctx.result(resultString).status(HttpURLConnection.HTTP_OK).contentType("text/html; charset=utf-8");
                    } else {
                        String sentimentPlainText = doc.getCategoryLocalizedPlainText();
                        ctx.result(sentimentPlainText).status(HttpURLConnection.HTTP_OK).contentType("text/html; charset=utf-8");
                    }
                }
                break;

                case "html": {
                    HtmlSettings htmlSettings = new HtmlSettings();
                    String explanationInHtml = UmigonExplain.getExplanationOfHeuristicOrganicResultsHtml(doc, langExplanation.trim(), htmlSettings, withoutContactAndTextTitleBoolean);
                    String shortAnswerInHtml = "<p>" + UmigonExplain.getOrganicPlainText(doc.getCategorizationResult(), langExplanation);
                    if (explanationParam != null && explanationParam.trim().toLowerCase().equals("on")) {
                        ctx.result(explanationInHtml).status(HttpURLConnection.HTTP_OK).contentType("text/html; charset=utf-8");
                    } else {
                        ctx.result(shortAnswerInHtml).status(HttpURLConnection.HTTP_OK).contentType("text/html; charset=utf-8");
                    }
                }
                break;

                case "bytes": {
                    HtmlSettings htmlSettingsForBytes = new HtmlSettings();
                    String explanationInHtmlForBytes = UmigonExplain.getExplanationOfHeuristicOrganicResultsHtml(doc, langExplanation.trim(), htmlSettingsForBytes, withoutContactAndTextTitleBoolean);
                    String explanationInPlainTextForBytes = UmigonExplain.getExplanationOfHeuristicOrganicResultsPlainText(doc, langExplanation.trim());
                    doc.setExplanationHtml(explanationInHtmlForBytes);
                    doc.setExplanationPlainText(explanationInPlainTextForBytes);
                    ctx.result(APIController.byteArraySerializerForDocuments(doc)).status(HttpURLConnection.HTTP_OK);
                }
                break;

                case "json": {
                    if (explanationParam != null && explanationParam.trim().toLowerCase().equals("on")) {
                        jsonAnswer.add("info", contactPointPlain);
                        jsonAnswer.addAll(UmigonExplain.getExplanationOfHeuristicResultsJson(doc, langExplanation.trim()));
                        if (!doc.getDecisions().isEmpty()) {
                            jsonAnswer.addAll(UmigonExplain.getExplanationsOfDecisionsJsonObject(doc, langExplanation.trim()));
                        }
                        ctx.json(APIController.turnJsonObjectToString(jsonAnswer.build())).status(HttpURLConnection.HTTP_OK).contentType("application/json; charset=utf-8");
                    } else {
                        JsonObjectBuilder job = Json.createObjectBuilder();
                        job.add("sentiment", doc.getCategoryLocalizedPlainText());
                        String result = APIController.turnJsonObjectToString(job.build());
                        ctx.result(result).status(HttpURLConnection.HTTP_OK).contentType("application/json; charset=utf-8");
                    }
                }
                break;

                default: {
                    jsonAnswer.addAll(UmigonExplain.getExplanationOfHeuristicResultsJson(doc, langExplanation.trim()));
                    if (!doc.getDecisions().isEmpty()) {
                        jsonAnswer.addAll(UmigonExplain.getExplanationsOfDecisionsJsonObject(doc, langExplanation.trim()));
                    }
                    ctx.json(APIController.turnJsonObjectToString(jsonAnswer.build())).status(HttpURLConnection.HTTP_OK).contentType("application/json; charset=utf-8");
                }
            }
        });

        app.get("/api/organicForAText/json/{lang}", (Context ctx) -> {
            JsonObjectBuilder jsonAnswer = Json.createObjectBuilder();
            jsonAnswer.add("info and questions", "admin@clementlevallois.net");
            String owner = ctx.queryParam("owner");
            if (owner == null || !owner.equals(APIController.pwdOwner)) {
                NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
                increment();
            }
            String text = ctx.queryParam("text");
            if (text == null || text.isBlank()) {
                ctx.json("{\"error\":\"query parameter *text* is missing\"}").status(HttpURLConnection.HTTP_BAD_REQUEST);
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
                        ctx.json("\"wrong param for lang\":\"lang not supported\"").status(HttpURLConnection.HTTP_BAD_REQUEST);
                }
                String explanationParam = ctx.queryParam("explanation");
                String langExplanation = ctx.queryParam("explanation_lang");
                if (langExplanation == null) {
                    langExplanation = "en";
                }
                if (explanationParam != null && explanationParam.toLowerCase().equals("on")) {
                    JsonObjectBuilder explanationOfHeuristicResultsJson = UmigonExplain.getExplanationOfHeuristicOrganicResultsJson(doc, langExplanation);
                    jsonAnswer.addAll(explanationOfHeuristicResultsJson);
                    if (!doc.getDecisions().isEmpty()) {
                        jsonAnswer.add("deciding between different heuristics", UmigonExplain.getExplanationsOfDecisionsJsonObject(doc, langExplanation));
                    }

                } else {
                    jsonAnswer.add("organic", UmigonExplain.getOrganicPlainText(doc, langExplanation));
                }
                JsonObject jsonToSend = jsonAnswer.build();
                ctx.json(APIController.turnJsonObjectToString(jsonToSend)).status(HttpURLConnection.HTTP_OK);

            }
        });

        app.get("/api/organicForAText/bytes/{lang}", ctx -> {
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
                ctx.result(APIController.byteArraySerializerForDocuments(docInput)).status(HttpURLConnection.HTTP_BAD_REQUEST);
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
                        ctx.result(APIController.byteArraySerializerForDocuments(docInput)).status(HttpURLConnection.HTTP_BAD_REQUEST);
                }
                ctx.result(APIController.byteArraySerializerForDocuments(docOutput)).status(HttpURLConnection.HTTP_OK);
            }
        }
        );
        return app;
    }
}