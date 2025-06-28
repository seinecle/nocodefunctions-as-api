package net.clementlevallois.nocodefunctionswebservices.pdfmatcher;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference; // IMP: Added for mutable references

import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.FunctionPdfMatcher;
import net.clementlevallois.functions.model.Occurrence;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import net.clementlevallois.pdfmatcher.controller.PdfMatcher;

public class PdfMatcherEndPoints {

    public static Javalin addAll(Javalin app) throws Exception {

        app.post(Globals.API_ENDPOINT_ROOT + FunctionPdfMatcher.ENDPOINT, ctx -> {
            JsonObjectBuilder errorBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            // Use AtomicReference for variables that need to be modified by lambdas
            final AtomicReference<TreeMap<Integer, String>> linesRef = new AtomicReference<>();
            final AtomicReference<TreeMap<Integer, Integer>> pagesRef = new AtomicReference<>();
            final AtomicReference<Integer> nbWordsRef = new AtomicReference<>();
            final AtomicReference<Integer> nbLinesRef = new AtomicReference<>();
            final AtomicReference<String> searchedTermRef = new AtomicReference<>();
            final AtomicReference<Boolean> caseSensitiveRef = new AtomicReference<>();
            final AtomicReference<String> endOfPageRef = new AtomicReference<>();
            final AtomicReference<String> startOfPageRef = new AtomicReference<>();
            final AtomicReference<String> typeOfContextRef = new AtomicReference<>();
            final AtomicReference<String> fileNameRef = new AtomicReference<>();

            // 1. Parse JSON body parameters and populate mutable variables via AtomicReference
            if (!parseBody(ctx, errorBuilder,
                    linesRef::set, // Use method reference for setting AtomicReference
                    pagesRef::set)) {
                ctx.result(errorBuilder.build().toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }

            // 2. Parse Query parameters and populate mutable variables via AtomicReference
            parseQueryParams(ctx,
                    startOfPageRef::set,
                    endOfPageRef::set,
                    typeOfContextRef::set,
                    caseSensitiveRef::set,
                    searchedTermRef::set,
                    fileNameRef::set,
                    nbWordsRef::set,
                    nbLinesRef::set);

            // Retrieve values from AtomicReferences, applying default values where needed
            String startOfPage = Optional.ofNullable(startOfPageRef.get()).orElse("start of page");
            String endOfPage = Optional.ofNullable(endOfPageRef.get()).orElse("end of page");
            Boolean caseSensitive = Optional.ofNullable(caseSensitiveRef.get()).orElse(false);

            final PdfMatchingRequest request = new PdfMatchingRequest(
                    linesRef.get(),
                    pagesRef.get(),
                    nbWordsRef.get(),
                    nbLinesRef.get(),
                    searchedTermRef.get(),
                    caseSensitive,
                    endOfPage,
                    startOfPage,
                    typeOfContextRef.get(),
                    fileNameRef.get()
            );

            // Ensure mandatory fields from JSON body are present after record creation
            if (request.lines() == null || request.lines().isEmpty()) {
                errorBuilder.add("-99", "No lines in the request payload.");
                ctx.result(errorBuilder.build().toString()).status(HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }

            PdfMatcher pdfMatcher = new PdfMatcher();
            List<Occurrence> occurrences = pdfMatcher.analyze(
                    request.pages(),
                    request.searchedTerm(),
                    request.lines(),
                    request.nbWords(),
                    request.nbLines(),
                    request.caseSensitive(),
                    request.startOfPage(),
                    request.endOfPage());

            if (occurrences == null) {
                ctx.result("error on the pdf occurrences API - occurrences were null".getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_INTERNAL_ERROR);
            } else {
                ctx.result(APIController.byteArraySerializerForListOfOccurrences(occurrences)).status(HttpURLConnection.HTTP_OK);
            }
        });

        return app;
    }

    /**
     * Parses the JSON request body. Populates the record fields via Consumers.
     * Uses a switch expression on FunctionPdfMatcher.BodyParams enum for
     * exhaustiveness.
     */
    private static boolean parseBody(Context ctx, JsonObjectBuilder errorBuilder,
            Consumer<TreeMap<Integer, String>> linesSetter,
            Consumer<TreeMap<Integer, Integer>> pagesSetter) {
        try {
            String body = ctx.body();
            if (body.isEmpty()) {
                errorBuilder.add("-99", "body of the request should not be empty");
                return false;
            }

            JsonReader jsonReader = Json.createReader(new StringReader(body));
            JsonObject jsonObject = jsonReader.readObject();

            for (var entry : jsonObject.entrySet()) {
                String key = entry.getKey();
                Optional<FunctionPdfMatcher.BodyParams> bp = APIController.enumValueOf(FunctionPdfMatcher.BodyParams.class, key);

                if (bp.isPresent()) {
                    Consumer<JsonObject> bodyParamHandler = switch (bp.get()) {
                        case LINES ->
                            json -> {
                                TreeMap<Integer, String> linesMap = new TreeMap<>();
                                JsonObject linesJson = json.getJsonObject(key);
                                for (String lineKey : linesJson.keySet()) {
                                    linesMap.put(Integer.valueOf(lineKey), linesJson.getString(lineKey));
                                }
                                linesSetter.accept(linesMap);
                            };
                        case PAGES ->
                            json -> {
                                TreeMap<Integer, Integer> pagesMap = new TreeMap<>();
                                JsonObject pagesJson = json.getJsonObject(key);
                                for (String pageKey : pagesJson.keySet()) {
                                    pagesMap.put(Integer.valueOf(pageKey), pagesJson.getInt(pageKey));
                                }
                                pagesSetter.accept(pagesMap);
                            };
                    };
                    bodyParamHandler.accept(jsonObject);
                } else {
                    System.out.println("PdfMatcherEndPoints: Unknown body parameter key: " + key);
                }
            }
        } catch (Exception e) {
            errorBuilder.add("-99", "Failed to parse request body: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Parses the query parameters. Populates the record fields via Consumers.
     * Uses a switch expression on FunctionPdfMatcher.QueryParams enum for
     * exhaustiveness.

     */
    private static void parseQueryParams(Context ctx,
            Consumer<String> startOfPageSetter,
            Consumer<String> endOfPageSetter,
            Consumer<String> typeOfContextSetter,
            Consumer<Boolean> caseSensitiveSetter,
            Consumer<String> searchedTermSetter,
            Consumer<String> fileNameSetter,
            Consumer<Integer> nbWordsSetter,
            Consumer<Integer> nbLinesSetter) {
        for (var entry : ctx.queryParamMap().entrySet()) {
            String key = entry.getKey();
            String decodedParamValue = URLDecoder.decode(entry.getValue().getFirst(), StandardCharsets.UTF_8);

            Optional<FunctionPdfMatcher.QueryParams> qp = APIController.enumValueOf(FunctionPdfMatcher.QueryParams.class, key);

            if (qp.isPresent()) {
                Consumer<String> queryParamHandler = switch (qp.get()) {
                    case START_OF_PAGE ->
                        startOfPageSetter;
                    case END_OF_PAGE ->
                        endOfPageSetter;
                    case TYPE_OF_CONTEXT ->
                        typeOfContextSetter;
                    case CASE_SENSITIVE ->
                        s -> caseSensitiveSetter.accept(Boolean.valueOf(s));
                    case SEARCHED_TERM ->
                        searchedTermSetter;
                    case FILE_NAME ->
                        fileNameSetter;
                    case NB_WORDS ->
                        s -> nbWordsSetter.accept(Integer.valueOf(s));
                    case NB_LINES ->
                        s -> nbLinesSetter.accept(Integer.valueOf(s));
                };
                queryParamHandler.accept(decodedParamValue);
            } else {
                System.out.println("PdfMatcherEndPoints: Unknown query parameter key: " + key);
            }
        }
    }
}
