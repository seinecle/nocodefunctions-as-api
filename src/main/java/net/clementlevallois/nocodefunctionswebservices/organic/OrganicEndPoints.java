package net.clementlevallois.nocodefunctionswebservices.organic;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import net.clementlevallois.umigon.explain.controller.UmigonExplain;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.FunctionOrganic;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.umigon.model.classification.Document;
import net.clementlevallois.umigon.classifier.controller.UmigonController;
import net.clementlevallois.umigon.classifier.organic.ClassifierOrganicOneDocument;
import net.clementlevallois.umigon.explain.parameters.HtmlSettings;

/**
 *
 * @author LEVALLOIS
 */
public class OrganicEndPoints {

    private static ClassifierOrganicOneDocument classifierOneDocEN;
    private static ClassifierOrganicOneDocument classifierOneDocFR;

    public static void initSentimentClassifiers(UmigonController umigonController) {
        classifierOneDocEN = new ClassifierOrganicOneDocument(umigonController.getSemanticsEN());
        classifierOneDocFR = new ClassifierOrganicOneDocument(umigonController.getSemanticsFR());
    }

    public static Javalin addAll(Javalin app) {

        app.get(Globals.API_ENDPOINT_ROOT + FunctionOrganic.ENDPOINT, ctx -> {
            JsonObjectBuilder jsonAnswer = Json.createObjectBuilder();

            final AtomicReference<String> ownerRef = new AtomicReference<>();
            final AtomicReference<String> textRef = new AtomicReference<>();
            final AtomicReference<Boolean> shorterRef = new AtomicReference<>();
            final AtomicReference<String> textLangRef = new AtomicReference<>();
            final AtomicReference<String> outputFormatRef = new AtomicReference<>();
            final AtomicReference<String> idRef = new AtomicReference<>();
            final AtomicReference<String> explanationRef = new AtomicReference<>();
            final AtomicReference<String> explanationLangRef = new AtomicReference<>();

            parseQueryParams(ctx,
                    ownerRef::set,
                    textRef::set,
                    shorterRef::set,
                    textLangRef::set,
                    outputFormatRef::set,
                    idRef::set,
                    explanationRef::set,
                    explanationLangRef::set);

            String owner = ownerRef.get();
            String text = Optional.ofNullable(textRef.get()).orElse("").trim();
            Boolean shorter = Optional.ofNullable(shorterRef.get()).orElse(false);
            String textLang = Optional.ofNullable(textLangRef.get()).orElse("en").trim();
            String outputFormatStr = Optional.ofNullable(outputFormatRef.get()).orElse("plain").trim();
            String id = Optional.ofNullable(idRef.get()).orElse(UUID.randomUUID().toString().substring(0, 10));
            String explanation = explanationRef.get();
            String explanationLang = Optional.ofNullable(explanationLangRef.get()).orElse("en").trim();

            final OrganicRequest request = new OrganicRequest(
                    owner,
                    text,
                    shorter,
                    textLang,
                    outputFormatStr,
                    id,
                    explanation,
                    explanationLang
            );

            if (request.owner() == null || !request.owner().equals(APIController.pwdOwner)) {
                NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
                APIController.increment();
            }

            // Create the initial Document object
            Document tempDoc = new Document();
            tempDoc.setText(request.text());
            tempDoc.setId(request.id());

            // Apply classification based on text language
            final Document classifiedDoc; // Declare as final
            switch (request.textLang()) {
                case "en" ->
                    classifiedDoc = classifierOneDocEN.call(tempDoc);
                case "fr" ->
                    classifiedDoc = classifierOneDocFR.call(tempDoc);
                default -> {
                    classifiedDoc = classifierOneDocEN.call(tempDoc);
                    System.out.println("OrganicEndPoints: Unsupported text language: " + request.textLang() + ". Defaulting to 'en'.");
                }
            }

            // Enrich document with plain text results. This is the final state of the Document.
            final Document finalDoc = UmigonExplain.enrichDocWithPlainTextOrganicResults(classifiedDoc, request.explanationLang());

            String contactPointPlain = "Made with https://nocodefunctions.com. Remarks, questions, corrections: analysis@exploreyourdata.com";
            boolean explanationOn = Optional.ofNullable(request.explanation())
                    .map(s -> s.trim().toLowerCase().equals("on"))
                    .orElse(false);

            FunctionOrganic.OutputFormat outputFormat = APIController.enumValueOf(FunctionOrganic.OutputFormat.class, request.outputFormat())
                    .orElse(FunctionOrganic.OutputFormat.PLAIN);

            // The 'outputHandler' now captures the 'finalDoc' which is effectively final
            Consumer<Context> outputHandler = switch (outputFormat) {
                case PLAIN ->
                    context -> {
                        if (explanationOn) {
                            String explanationInPlain = UmigonExplain.getExplanationOfHeuristicOrganicResultsPlainText(finalDoc, request.explanationLang());
                            if (!finalDoc.getDecisions().isEmpty()) {
                                explanationInPlain = explanationInPlain + " " + UmigonExplain.getExplanationsOfDecisionsPlainText(finalDoc, request.explanationLang());
                            }
                            String resultString = "*** " + contactPointPlain + " ***" + "\n\n" + explanationInPlain;
                            context.result(resultString).status(HttpURLConnection.HTTP_OK).contentType("text/html; charset=utf-8");
                        } else {
                            String sentimentPlainText = finalDoc.getCategoryLocalizedPlainText();
                            context.result(sentimentPlainText).status(HttpURLConnection.HTTP_OK).contentType("text/html; charset=utf-8");
                        }
                    };

                case HTML ->
                    context -> {
                        HtmlSettings htmlSettings = new HtmlSettings();
                        String explanationInHtml = UmigonExplain.getExplanationOfHeuristicOrganicResultsHtml(finalDoc, request.explanationLang(), htmlSettings, request.shorter());
                        String shortAnswerInHtml = "<p>" + UmigonExplain.getOrganicPlainText(finalDoc.getCategorizationResult(), request.explanationLang());
                        if (explanationOn) {
                            context.result(explanationInHtml).status(HttpURLConnection.HTTP_OK).contentType("text/html; charset=utf-8");
                        } else {
                            context.result(shortAnswerInHtml).status(HttpURLConnection.HTTP_OK).contentType("text/html; charset=utf-8");
                        }
                    };

                case BYTES ->
                    context -> {
                    HtmlSettings htmlSettingsForBytes = new HtmlSettings();
                    String explanationInHtmlForBytes = UmigonExplain.getExplanationOfHeuristicOrganicResultsHtml(finalDoc, request.explanationLang(), htmlSettingsForBytes, request.shorter());
                    String explanationInPlainTextForBytes = UmigonExplain.getExplanationOfHeuristicOrganicResultsPlainText(finalDoc, request.explanationLang());
                    finalDoc.setExplanationHtml(explanationInHtmlForBytes); // Modifying finalDoc's properties, not reassigning finalDoc itself
                    finalDoc.setExplanationPlainText(explanationInPlainTextForBytes); // Modifying finalDoc's properties
                    context.result(APIController.byteArraySerializerForDocuments(finalDoc)).status(HttpURLConnection.HTTP_OK);
                    };

                case JSON ->
                    context -> {
                        if (explanationOn) {
                            jsonAnswer.add("info", contactPointPlain);
                            jsonAnswer.addAll(UmigonExplain.getExplanationOfHeuristicResultsJson(finalDoc, request.explanationLang()));
                            if (!finalDoc.getDecisions().isEmpty()) {
                                jsonAnswer.addAll(UmigonExplain.getExplanationsOfDecisionsJsonObject(finalDoc, request.explanationLang()));
                            }
                            context.json(APIController.turnJsonObjectToString(jsonAnswer.build())).status(HttpURLConnection.HTTP_OK).contentType("application/json; charset=utf-8");
                        } else {
                            JsonObjectBuilder job = Json.createObjectBuilder();
                            job.add("sentiment", finalDoc.getCategoryLocalizedPlainText());
                            String result = APIController.turnJsonObjectToString(job.build());
                            context.result(result).status(HttpURLConnection.HTTP_OK).contentType("application/json; charset=utf-8");
                        }
                    };
            };

            outputHandler.accept(ctx);

        });

        return app;
    }

    /**
     * Parses the query parameters and populates the AtomicReferences for the
     * OrganicRequest record.
     */
    private static void parseQueryParams(Context ctx,
            Consumer<String> ownerSetter,
            Consumer<String> textSetter,
            Consumer<Boolean> shorterSetter,
            Consumer<String> textLangSetter,
            Consumer<String> outputFormatSetter,
            Consumer<String> idSetter,
            Consumer<String> explanationSetter,
            Consumer<String> explanationLangSetter) {
        for (var entry : ctx.queryParamMap().entrySet()) {
            String key = entry.getKey();
            String decodedParamValue = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);

            Optional<FunctionOrganic.QueryParams> qp = APIController.enumValueOf(FunctionOrganic.QueryParams.class, key);

            if (qp.isPresent()) {
                Consumer<String> queryParamHandler = switch (qp.get()) {
                    case OWNER ->
                        ownerSetter;
                    case TEXT ->
                        textSetter;
                    case SHORTER ->
                        s -> shorterSetter.accept(Boolean.valueOf(s));
                    case TEXT_LANG ->
                        textLangSetter;
                    case OUTPUT_FORMAT ->
                        outputFormatSetter;
                    case ID ->
                        idSetter;
                    case EXPLANATION ->
                        explanationSetter;
                    case EXPLANATION_LANG ->
                        explanationLangSetter;
                };
                queryParamHandler.accept(decodedParamValue);
            } else {
                System.out.println("OrganicEndPoints: Unknown query parameter key: " + key);
            }
        }
    }
}
