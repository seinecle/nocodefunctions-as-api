package net.clementlevallois.nocodefunctionswebservices.llms;

import io.javalin.Javalin;
import io.javalin.http.util.NaiveRateLimit;
import jakarta.json.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodefunctionswebservices.APIController;

public class LLMOpsEndpoints {

    public static Javalin addAll(Javalin app) {

        app.post("/api/llm-ops/getContextFromSample", ctx -> {
            NaiveRateLimit.requestPerTimeUnit(ctx, 50, TimeUnit.SECONDS);

            var body = ctx.body();
            if (body.isEmpty()) {
                ctx.result(Json.createObjectBuilder()
                        .add("-99", "body of the request should not be empty")
                        .build()
                        .toString())
                        .status(HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }

            var json = Json.createReader(new StringReader(body)).readObject();
            var langSource = json.getString("langSource", "en");
            var langTarget = json.getString("langTarget", "en");

            var rawText = getTextFromTempFile(json.getString("dataPersistenceId", null));

            if (rawText == null || rawText.isBlank()) {
                ctx.result("No content found for processing").status(HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }

            var runnable = new RunnableContextFromSample();
            runnable.runContextFromSampleInBackgroundThread(rawText,langSource,langTarget);
            ctx.result("OK".getBytes()).status(HttpURLConnection.HTTP_OK);
        });

        return app;
    }

    private static String getTextFromTempFile(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            var path = Path.of(APIController.tempFilesFolder.toString(), id);
            if (Files.exists(path) && !Files.isDirectory(path)) {
                var content = Files.readString(path, StandardCharsets.UTF_8);
                Files.delete(path);
                return content;
            }
        } catch (IOException e) {
            Logger.getLogger(LLMOpsEndpoints.class.getName()).log(Level.SEVERE, null, e);
        }
        return null;
    }

    public static byte[] byteArraySerializerForAnyObject(Object o) {
        try (var baos = new ByteArrayOutputStream(); var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(o);
            return baos.toByteArray();
        } catch (IOException ex) {
            Logger.getLogger(LLMOpsEndpoints.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
}
