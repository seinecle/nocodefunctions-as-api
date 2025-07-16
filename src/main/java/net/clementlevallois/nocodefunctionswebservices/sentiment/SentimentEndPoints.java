/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.sentiment;

import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.FunctionUmigon;
import static net.clementlevallois.functions.model.FunctionUmigon.QueryParams.EXPLANATION_LANG;
import static net.clementlevallois.functions.model.FunctionUmigon.QueryParams.OWNER;
import static net.clementlevallois.functions.model.FunctionUmigon.QueryParams.SHORTER;
import static net.clementlevallois.functions.model.FunctionUmigon.QueryParams.TEXT_LANG;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.CALLBACK_URL;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.JOB_ID;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import static net.clementlevallois.nocodefunctionswebservices.APIController.enumValueOf;
import static net.clementlevallois.nocodefunctionswebservices.APIController.increment;
import net.clementlevallois.umigon.classifier.controller.UmigonController;
import net.clementlevallois.umigon.classifier.sentiment.ClassifierSentimentOneDocument;
import net.clementlevallois.umigon.explain.controller.UmigonExplain;
import net.clementlevallois.umigon.explain.parameters.HtmlSettings;
import net.clementlevallois.umigon.model.classification.Document;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class SentimentEndPoints {

    private static ClassifierSentimentOneDocument classifierOneDocEN;
    private static ClassifierSentimentOneDocument classifierOneDocFR;
    private static ClassifierSentimentOneDocument classifierOneDocES;

    public static void initSentimentClassifiers(UmigonController umigonController) {
        classifierOneDocEN = new ClassifierSentimentOneDocument(umigonController.getSemanticsEN());
        classifierOneDocFR = new ClassifierSentimentOneDocument(umigonController.getSemanticsFR());
        classifierOneDocES = new ClassifierSentimentOneDocument(umigonController.getSemanticsES());
    }

    public static Javalin addAll(Javalin app) {

        app.get(Globals.API_ENDPOINT_ROOT + FunctionUmigon.ENDPOINT, ctx -> {
            JsonObjectBuilder jsonAnswer = Json.createObjectBuilder();

            String owner = ctx.queryParam("owner");
            if (owner == null || !owner.equals(APIController.pwdOwner)) {
                NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
                increment();
            }
            String text = ctx.queryParam("text");
            String withoutContactAndTextTitle = ctx.queryParam("shorter");
            Boolean withoutContactAndTextTitleBoolean;
            withoutContactAndTextTitleBoolean = withoutContactAndTextTitle != null && (withoutContactAndTextTitle.equals("true") | withoutContactAndTextTitle.equals("false")) && Boolean.parseBoolean(withoutContactAndTextTitle);

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
                case "en" ->
                    doc = classifierOneDocEN.call(doc);
                case "fr" ->
                    doc = classifierOneDocFR.call(doc);
                case "es" ->
                    doc = classifierOneDocES.call(doc);
            }
            String explanationParam = ctx.queryParam("explanation");
            String langExplanation = ctx.queryParam("explanation-lang");
            if (langExplanation == null || langExplanation.isBlank()) {
                langExplanation = "en";
            }
            doc = UmigonExplain.enrichDocWithPlainTextSentimentResults(doc, langExplanation.trim());

            String contactPointPlain = "Made with https://nocodefunctions.com. Remarks, questions, corrections: analysis@exploreyourdata.com";

            switch (outputFormat.trim()) {
                case "plain" -> {
                    if (explanationParam == null || !explanationParam.trim().toLowerCase().equals("on")) {
                        String sentimentPlainText = doc.getCategoryLocalizedPlainText();
                        ctx.result(sentimentPlainText).status(HttpURLConnection.HTTP_OK).contentType("text/html; charset=utf-8");
                    } else {
                        String explanationInPlain = UmigonExplain.getExplanationOfHeuristicResultsPlainText(doc, langExplanation.trim());
                        if (!doc.getDecisions().isEmpty()) {
                            explanationInPlain = explanationInPlain + " " + UmigonExplain.getExplanationsOfDecisionsPlainText(doc, langExplanation.trim());
                        }
                        String resultString = "*** " + contactPointPlain + " ***" + "\n\n" + explanationInPlain;
                        ctx.result(resultString).status(HttpURLConnection.HTTP_OK).contentType("text/html; charset=utf-8");
                    }
                }

                case "html" -> {
                    HtmlSettings htmlSettings = new HtmlSettings();
                    String explanationInHtml = UmigonExplain.getExplanationOfHeuristicResultsHtml(doc, langExplanation.trim(), htmlSettings, withoutContactAndTextTitleBoolean);
                    String shortAnswerInHtml = "<p>" + UmigonExplain.getSentimentPlainText(doc.getCategorizationResult(), langExplanation);
                    if (explanationParam != null && explanationParam.trim().toLowerCase().equals("on")) {
                        ctx.result(explanationInHtml).status(HttpURLConnection.HTTP_OK).contentType("text/html; charset=utf-8");
                    } else {
                        ctx.result(shortAnswerInHtml).status(HttpURLConnection.HTTP_OK).contentType("text/html; charset=utf-8");
                    }
                }

                case "bytes" -> {
                    HtmlSettings htmlSettingsForBytes = new HtmlSettings();
                    String explanationInHtmlForBytes = UmigonExplain.getExplanationOfHeuristicResultsHtml(doc, langExplanation.trim(), htmlSettingsForBytes, withoutContactAndTextTitleBoolean);
                    String explanationInPlainTextForBytes = UmigonExplain.getExplanationOfHeuristicResultsPlainText(doc, langExplanation.trim());
                    doc.setExplanationHtml(explanationInHtmlForBytes);
                    doc.setExplanationPlainText(explanationInPlainTextForBytes);
                    ctx.result(APIController.byteArraySerializerForDocuments(doc)).status(HttpURLConnection.HTTP_OK);
                }

                case "json" -> {
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

                default -> {
                    jsonAnswer.addAll(UmigonExplain.getExplanationOfHeuristicResultsJson(doc, langExplanation.trim()));
                    if (!doc.getDecisions().isEmpty()) {
                        jsonAnswer.addAll(UmigonExplain.getExplanationsOfDecisionsJsonObject(doc, langExplanation.trim()));
                    }
                    ctx.json(APIController.turnJsonObjectToString(jsonAnswer.build())).status(HttpURLConnection.HTTP_OK).contentType("application/json; charset=utf-8");
                }
            }
        });

        app.post(Globals.API_ENDPOINT_ROOT + FunctionUmigon.ENDPOINT, ctx -> {
            JsonObjectBuilder jsonAnswer = Json.createObjectBuilder();

            String owner = ctx.queryParam("owner");
            if (owner == null || !owner.equals(APIController.pwdOwner)) {
                NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
                increment();
            }
            String text = ctx.body();
            String withoutContactAndTextTitle = ctx.queryParam("shorter");
            Boolean withoutContactAndTextTitleBoolean;
            withoutContactAndTextTitleBoolean = withoutContactAndTextTitle != null && (withoutContactAndTextTitle.equals("true") | withoutContactAndTextTitle.equals("false")) && Boolean.parseBoolean(withoutContactAndTextTitle);

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
                case "en" ->
                    doc = classifierOneDocEN.call(doc);
                case "fr" ->
                    doc = classifierOneDocFR.call(doc);

                case "es" ->
                    doc = classifierOneDocES.call(doc);
            }
            String explanationParam = ctx.queryParam("explanation");
            String langExplanation = ctx.queryParam("explanation-lang");
            if (langExplanation == null || langExplanation.isBlank()) {
                langExplanation = "en";
            }
            doc = UmigonExplain.enrichDocWithPlainTextSentimentResults(doc, langExplanation.trim());

            String contactPointPlain = "Made with https://nocodefunctions.com. Remarks, questions, corrections: analysis@exploreyourdata.com";

            switch (outputFormat.trim()) {
                case "plain" -> {
                    if (explanationParam != null && explanationParam.trim().toLowerCase().equals("on")) {
                        String explanationInPlain = UmigonExplain.getExplanationOfHeuristicResultsPlainText(doc, langExplanation.trim());
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

                case "html" -> {
                    HtmlSettings htmlSettings = new HtmlSettings();
                    String explanationInHtml = UmigonExplain.getExplanationOfHeuristicResultsHtml(doc, langExplanation.trim(), htmlSettings, withoutContactAndTextTitleBoolean);
                    String shortAnswerInHtml = "<p>" + UmigonExplain.getSentimentPlainText(doc.getCategorizationResult(), langExplanation);
                    if (explanationParam != null && explanationParam.trim().toLowerCase().equals("on")) {
                        ctx.result(explanationInHtml).status(HttpURLConnection.HTTP_OK).contentType("text/html; charset=utf-8");
                    } else {
                        ctx.result(shortAnswerInHtml).status(HttpURLConnection.HTTP_OK).contentType("text/html; charset=utf-8");
                    }
                }

                case "bytes" -> {
                    HtmlSettings htmlSettingsForBytes = new HtmlSettings();
                    String explanationInHtmlForBytes = UmigonExplain.getExplanationOfHeuristicResultsHtml(doc, langExplanation.trim(), htmlSettingsForBytes, withoutContactAndTextTitleBoolean);
                    String explanationInPlainTextForBytes = UmigonExplain.getExplanationOfHeuristicResultsPlainText(doc, langExplanation.trim());
                    doc.setExplanationHtml(explanationInHtmlForBytes);
                    doc.setExplanationPlainText(explanationInPlainTextForBytes);
                    ctx.result(APIController.byteArraySerializerForDocuments(doc)).status(HttpURLConnection.HTTP_OK);
                }

                case "json" -> {
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

                default -> {
                    jsonAnswer.addAll(UmigonExplain.getExplanationOfHeuristicResultsJson(doc, langExplanation.trim()));
                    if (!doc.getDecisions().isEmpty()) {
                        jsonAnswer.addAll(UmigonExplain.getExplanationsOfDecisionsJsonObject(doc, langExplanation.trim()));
                    }
                    ctx.json(APIController.turnJsonObjectToString(jsonAnswer.build())).status(HttpURLConnection.HTTP_OK).contentType("application/json; charset=utf-8");
                }
            }
        });

        app.post(Globals.API_ENDPOINT_ROOT + FunctionUmigon.ENDPOINT_DATA_FROM_FILE, ctx -> {
            JsonObjectBuilder jsonAnswer = Json.createObjectBuilder();

            List<String> lines = new ArrayList();

            List<Document> docs = new ArrayList();

            AtomicReference<String> textLangRef = new AtomicReference<>();
            AtomicReference<Boolean> shorterRef = new AtomicReference<>();
            AtomicReference<String> explanationRef = new AtomicReference<>();
            AtomicReference<String> explanationLangRef = new AtomicReference<>();
            AtomicReference<String> outputFormatRef = new AtomicReference<>();
            AtomicReference<String> ownerRef = new AtomicReference<>();
            AtomicReference<String> sessionIdRef = new AtomicReference<>();
            AtomicReference<String> callbackURLRef = new AtomicReference<>();
            AtomicReference<String> jobIdRef = new AtomicReference<>();
            for (var entry : ctx.queryParamMap().entrySet()) {
                String key = entry.getKey();
                String decodedParamValue = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);

                Optional<FunctionUmigon.QueryParams> qp = enumValueOf(FunctionUmigon.QueryParams.class, key);
                Optional<Globals.GlobalQueryParams> gqp = enumValueOf(Globals.GlobalQueryParams.class, key);

                if (qp.isPresent()) {
                    Consumer<String> qpHandler = switch (qp.get()) {
                        case TEXT_LANG ->
                            s -> textLangRef.set(s);
                        case EXPLANATION ->
                            s -> explanationRef.set(s);
                        case SHORTER ->
                            s -> shorterRef.set(Boolean.valueOf(s));
                        case OWNER ->
                            s -> ownerRef.set(s);
                        case OUTPUT_FORMAT ->
                            s -> outputFormatRef.set(s);
                        case EXPLANATION_LANG ->
                            s -> explanationLangRef.set(s);
                    };
                    qpHandler.accept(decodedParamValue);
                } else if (gqp.isPresent()) {
                    Consumer<String> gqpHandler = switch (gqp.get()) {
                        case CALLBACK_URL ->
                            s -> callbackURLRef.set(s);
                        case JOB_ID ->
                            s -> jobIdRef.set(s);
                    };
                    gqpHandler.accept(decodedParamValue);
                } else {
                    System.out.println("issue in workflow topic endpoint with unknown enum value");
                }
            }

            String textLang = textLangRef.get();
            Boolean shorter = shorterRef.get();
            String explanationLang = explanationLangRef.get();
            String owner = ownerRef.get();
            String sessionId = sessionIdRef.get();
            String callbackURL = callbackURLRef.get();
            String jobId = jobIdRef.get();

            Path inputFile = APIController.tempFilesFolder.resolve(jobId).resolve(jobId);
            if (Files.exists(inputFile) && !Files.isDirectory(inputFile)) {
                try {
                    lines = Files.readAllLines(inputFile, StandardCharsets.UTF_8);
                    Files.deleteIfExists(inputFile);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }

            if (owner == null || !owner.equals(APIController.pwdOwner)) {
                NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
                increment();
            }
            for (String line : lines) {
                Document doc = new Document();
                doc.setText(line.trim());
                String id = UUID.randomUUID().toString().substring(0, 10);
                doc.setId(id);

                switch (textLang.trim()) {
                    case "en" ->
                        doc = classifierOneDocEN.call(doc);
                    case "fr" ->
                        doc = classifierOneDocFR.call(doc);

                    case "es" ->
                        doc = classifierOneDocES.call(doc);
                }
                doc = UmigonExplain.enrichDocWithPlainTextSentimentResults(doc, explanationLang.trim());

                HtmlSettings htmlSettingsForBytes = new HtmlSettings();
                String explanationInHtmlForBytes = UmigonExplain.getExplanationOfHeuristicResultsHtml(doc, explanationLang.trim(), htmlSettingsForBytes, shorter);
                String explanationInPlainTextForBytes = UmigonExplain.getExplanationOfHeuristicResultsPlainText(doc, explanationLang.trim());
                doc.setExplanationHtml(explanationInHtmlForBytes);
                doc.setExplanationPlainText(explanationInPlainTextForBytes);
                docs.add(doc);
            }

            byte[] docsAsByteArray = APIController.byteArraySerializerForAnyObject(docs);

            Globals globalProps = new Globals(APIController.tempFilesFolder);

            Files.write(globalProps.getResultInBinaryFormat(jobId), docsAsByteArray);
            Files.writeString(globalProps.getWorkflowCompleteFilePath(jobId), "umigon job complete");

            ctx.result("OK").status(HttpURLConnection.HTTP_OK);

        });

        return app;
    }
}
