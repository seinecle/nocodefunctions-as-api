/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.bibliocoupling;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.clementlevallois.bibliocoupling.controller.BiblioCoupling;
import static net.clementlevallois.nocodefunctionswebservices.APIController.increment;

/**
 *
 * @author LEVALLOIS
 */
public class BibliocouplingEndPoints {

    private static BlockingQueue<Runnable> requestQueue;
    private static ConcurrentHashMap<Context, String> results;
    private static int delayBetweenOpenAlexCalls = 100;
//    private static int counterDebug = 0;
//    private static int counterResultsDebug = 0;

    public static Javalin addAll(Javalin app) throws Exception {

        requestQueue = new LinkedBlockingQueue();
        results = new ConcurrentHashMap();

        // Create a scheduled executor service to process requests with a delay
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

        // Submit tasks from the queue to the executor service with a delay
        executorService.scheduleWithFixedDelay(() -> {
            try {
                Runnable request = requestQueue.take();
                request.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, 0, delayBetweenOpenAlexCalls, TimeUnit.MILLISECONDS);

        app.get("/api/bibliocoupling/", ctx -> {
            increment();

            String identifier = ctx.queryParam("identifier");
            String fieldType = Optional.ofNullable(ctx.queryParam("fieldType")).orElse("title");
            if (identifier == null || identifier.isBlank()) {
                String errorMessage = "identifier should not be empty";
                ctx.result(errorMessage.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_BAD_REQUEST);
            }
            boolean isInsertedInQueue = requestQueue.offer(() -> {
                BiblioCoupling bib = new BiblioCoupling();
                String refs;
                if (fieldType.equals("title")) {
                    refs = bib.getCommaSeparatedCitedRefsForOneWorkViaTitle(identifier);
                } else {
                    refs = bib.getCommaSeparatedCitedRefsForOneWorkViaDOI(identifier);
                }
                String result = identifier + "|" + refs;
                if (fieldType.equals("doi") || (fieldType.equals("doi") & !refs.isBlank())) {
                    results.put(ctx, result);
                }
            });
            if (!results.contains(ctx) && fieldType.equals("title")) {
                requestQueue.offer(() -> {
                    String titleDiffComma = identifier.replaceAll("'", "’");
                    BiblioCoupling bib = new BiblioCoupling();
                    String refs = bib.getCommaSeparatedCitedRefsForOneWorkViaTitle(titleDiffComma);
                    String result = identifier + "|" + refs;
                    results.put(ctx, result);
                });
            }
            int loops = 0;
            while (!results.containsKey(ctx) && loops < 3000) {
                Thread.sleep(50);
                loops++;
            }
            String result = results.remove(ctx);
            if (result == null){
                result = "";
            }
            ctx.result(result.getBytes(StandardCharsets.UTF_8)).status(HttpURLConnection.HTTP_OK);
        }
        );
        return app;
    }
}
