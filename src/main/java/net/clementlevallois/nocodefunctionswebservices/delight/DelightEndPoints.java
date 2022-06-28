/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.delight;

import io.javalin.Javalin;
import io.javalin.http.HttpCode;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import static net.clementlevallois.nocodefunctionswebservices.APIController.increment;
import net.clementlevallois.umigon.classifier.delight.ClassifierDelightOneDocument;
import net.clementlevallois.umigon.classifier.delight.ClassifierDelightOneWordInADocument;
import net.clementlevallois.umigon.controller.UmigonController;
import net.clementlevallois.umigon.model.Category;
import net.clementlevallois.umigon.model.Category.CategoryEnum;
import net.clementlevallois.umigon.model.Document;
import net.clementlevallois.umigon.model.ResultOneHeuristics;

/**
 *
 * @author LEVALLOIS
 */
public class DelightEndPoints {

    public static Javalin addAll(Javalin app, UmigonController umigonController) throws Exception {

        ClassifierDelightOneDocument classifierOneDocEN = new ClassifierDelightOneDocument(umigonController.getSemanticsEN());
        ClassifierDelightOneDocument classifierOneDocFR = new ClassifierDelightOneDocument(umigonController.getSemanticsFR());
        ClassifierDelightOneWordInADocument classifierOneWordFR = new ClassifierDelightOneWordInADocument(umigonController.getSemanticsFR());
        ClassifierDelightOneWordInADocument classifierOneWordEN = new ClassifierDelightOneWordInADocument(umigonController.getSemanticsEN());

        app.post("/api/delightForOneTermInAText/{lang}", ctx -> {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            TreeMap<Integer, JsonObject> lines = new TreeMap();
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
                    JsonObject oneDocument = jsonObject.getJsonObject(iteratorKeys.next());
                    lines.put(i++, oneDocument);
                }
                ClassifierDelightOneWordInADocument classifier = null;
                String lang = ctx.pathParam("lang");
                switch (lang) {
                    case "en":
                        classifier = classifierOneWordEN;
                        break;

                    case "fr":
                        classifier = classifierOneWordFR;
                        break;

                    default:
                        objectBuilder.add("-99", "wrong param for lang - lang not supported");
                        JsonObject jsonObjectWrite = objectBuilder.build();
                        ctx.result(jsonObjectWrite.toString()).status(HttpCode.BAD_REQUEST);
                }
                for (Integer key : lines.keySet()) {
                    String result = classifier.call(lines.get(key).getString("term"), lines.get(key).getString("text"));
                    results.put(key, result);
                }
                ctx.json(results).status(HttpCode.OK);
            }
        });

        app.get("/api/delightForOneTermInAText/{lang}", ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            String term = ctx.queryParam("term");
            String text = ctx.queryParam("text");
            String result = "neutral";
            if (term == null || term.isBlank() || text == null || text.isBlank()) {
                objectBuilder.add("-99", "term in the request should not be null or empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpCode.BAD_REQUEST);
            } else {

                ClassifierDelightOneWordInADocument classifier = null;
                String lang = ctx.pathParam("lang");
                switch (lang) {
                    case "en":
                        classifier = classifierOneWordEN;
                        break;

                    case "fr":
                        classifier = classifierOneWordFR;
                        break;

                    default:
                        objectBuilder.add("-99", "wrong param for lang - lang not supported");
                        JsonObject jsonObjectWrite = objectBuilder.build();
                        ctx.result(jsonObjectWrite.toString()).status(HttpCode.BAD_REQUEST);
                }

                result = classifier.call(term, text);
                ctx.result(result).status(HttpCode.OK);
            }
        }
        );

        app.post("/api/delightForAText/{lang}", ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 2, TimeUnit.SECONDS);
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            increment();
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
                ClassifierDelightOneDocument classifier = null;
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
                    Map<Integer, ResultOneHeuristics> map1 = doc.getAllHeuristicsResultsForOneCategory(Category.CategoryEnum._17);
                    if (map1.isEmpty()) {
                        results.put(key, "no delight");
                    } else {
                        results.put(key, "delight");
                    }

                }
                ctx.json(results).status(HttpCode.OK);
            }
        });

        app.get("/api/delightForAText/text/{lang}", ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 2, TimeUnit.SECONDS);
            increment();
            String text = ctx.queryParam("text");
            if (text == null || text.isBlank()) {
                ctx.result(CategoryEnum._10.toString()).status(HttpCode.BAD_REQUEST);
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
                    Map<Integer, ResultOneHeuristics> map1 = doc.getAllHeuristicsResultsForOneCategory(Category.CategoryEnum._17);
                    if (map1.isEmpty()) {
                        ctx.result("no delight").status(HttpCode.OK);
                    } else {
                        ctx.result("delight").status(HttpCode.OK);
                    }

                
            }
        }
        );

        app.get("/api/delightForAText/bytes/{lang}", ctx -> {
            Document docInput = new Document();
            APIController.increment();
            String text = ctx.queryParam("text");
            String id = ctx.queryParam("id");
            if (id == null) {
                id = UUID.randomUUID().toString().substring(0, 10);
            }
            docInput.setId(id);
            if (text == null) {
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
