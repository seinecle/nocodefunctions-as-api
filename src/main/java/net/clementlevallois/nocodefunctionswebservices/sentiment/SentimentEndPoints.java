/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.sentiment;

import io.javalin.Javalin;
import io.javalin.http.HttpCode;
import io.javalin.http.util.NaiveRateLimit;
import java.io.StringReader;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import static net.clementlevallois.nocodefunctionswebservices.APIController.increment;
import net.clementlevallois.umigon.classifier.sentiment.ClassifierSentimentOneDocument;
import net.clementlevallois.umigon.classifier.sentiment.ClassifierSentimentOneWordInADocument;
import net.clementlevallois.umigon.controller.UmigonController;
import net.clementlevallois.umigon.model.Categories;
import net.clementlevallois.umigon.model.Document;

/**
 *
 * @author LEVALLOIS
 */
public class SentimentEndPoints {

    public static Javalin addAll(Javalin app, UmigonController umigonController) {

        ClassifierSentimentOneDocument classifierOneDocEN = new ClassifierSentimentOneDocument(umigonController.getSemanticsEN());
        ClassifierSentimentOneDocument classifierOneDocFR = new ClassifierSentimentOneDocument(umigonController.getSemanticsFR());
        ClassifierSentimentOneWordInADocument classifierOneWordFR = new ClassifierSentimentOneWordInADocument(umigonController.getSemanticsFR());
        ClassifierSentimentOneWordInADocument classifierOneWordEN = new ClassifierSentimentOneWordInADocument(umigonController.getSemanticsEN());

        app.post("/api/sentimentForOneTermInAText/{lang}", ctx -> {
            String owner = ctx.queryParam("owner");
            if (owner == null || !owner.equals(APIController.pwdOwner)) {
                NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
                increment();
            }
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
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
                ClassifierSentimentOneWordInADocument classifier = null;
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

        app.get("/api/sentimentForOneTermInAText/{lang}", ctx -> {
            String owner = ctx.queryParam("owner");
            if (owner == null || !owner.equals(APIController.pwdOwner)) {
                NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);
                increment();
            }
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            String term = ctx.queryParam("term");
            String text = ctx.queryParam("text");
            String result;
            if (term == null || term.isBlank() || text == null || text.isBlank()) {
                objectBuilder.add("-99", "term in the request should not be null or empty");
                JsonObject jsonObject = objectBuilder.build();
                ctx.result(jsonObject.toString()).status(HttpCode.BAD_REQUEST);
            } else {

                ClassifierSentimentOneWordInADocument classifier = null;
                String lang = ctx.pathParam("lang");
                switch (lang) {
                    case "en":
                        classifier = classifierOneWordEN;
                        break;

                    case "fr":
                        classifier = classifierOneWordFR;
                        break;

                    default:
                        classifier = classifierOneWordEN;
                        objectBuilder.add("-99", "wrong param for lang - lang not supported");
                        JsonObject jsonObjectWrite = objectBuilder.build();
                        ctx.result(jsonObjectWrite.toString()).status(HttpCode.BAD_REQUEST);
                }

                result = classifier.call(term, text);
                ctx.result(result).status(HttpCode.OK);
            }
        }
        );

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
                    results.put(key, doc.getSentiment().toString());
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
                ctx.result(Categories.Category._10.toString()).status(HttpCode.BAD_REQUEST);
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
                ctx.result(doc.getSentiment().toString()).status(HttpCode.OK);
            }
        }
        );

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
