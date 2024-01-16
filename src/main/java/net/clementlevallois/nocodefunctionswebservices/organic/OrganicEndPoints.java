/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.organic;

import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import java.net.HttpURLConnection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import static net.clementlevallois.nocodefunctionswebservices.APIController.increment;
import net.clementlevallois.umigon.classifier.controller.UmigonController;
import net.clementlevallois.umigon.classifier.organic.ClassifierOrganicOneDocument;
import net.clementlevallois.umigon.explain.controller.UmigonExplain;
import net.clementlevallois.umigon.explain.parameters.HtmlSettings;
import net.clementlevallois.umigon.model.classification.Document;

/**
 *
 * @author LEVALLOIS
 */
public class OrganicEndPoints {

    public static Javalin addAll(Javalin app, UmigonController umigonController) {

        ClassifierOrganicOneDocument classifierOneDocEN = new ClassifierOrganicOneDocument(umigonController.getSemanticsEN());
        ClassifierOrganicOneDocument classifierOneDocFR = new ClassifierOrganicOneDocument(umigonController.getSemanticsFR());

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

            String contactPointPlain = "Made with https://nocodefunctions.com. Remarks, questions, corrections: analysis@exploreyourdata.com";

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

        app.post("/api/organicForAText", ctx -> {
            JsonObjectBuilder jsonAnswer = Json.createObjectBuilder();

            String owner = ctx.queryParam("owner");
            if (owner == null || !owner.equals(APIController.pwdOwner)) {
                NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
                increment();
            }
            String text = ctx.body();
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

            String contactPointPlain = "Made with https://nocodefunctions.com. Remarks, questions, corrections: analysis@exploreyourdata.com";

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

        return app;
    }
}