/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices;

import io.javalin.Javalin;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.Occurrence;
import net.clementlevallois.functions.model.WorkflowCowoProps;
import net.clementlevallois.nocodefunctionswebservices.cowo.CowoEndPoint;
import net.clementlevallois.nocodefunctionswebservices.sentiment.SentimentEndPoints;
import net.clementlevallois.nocodefunctionswebservices.workflow.gaze.GazeEndPoint;
import net.clementlevallois.nocodefunctionswebservices.graphops.GraphOpsEndPoint;
import net.clementlevallois.nocodefunctionswebservices.lemmatizerlight.LemmatizerLightEndPoint;
import net.clementlevallois.llm.functions.LLMsOps;
import net.clementlevallois.nocodefunctionswebservices.llms.LLMOpsEndpoints;
import net.clementlevallois.nocodefunctionswebservices.organic.OrganicEndPoints;
import net.clementlevallois.nocodefunctionswebservices.pdfmatcher.PdfMatcherEndPoints;
import net.clementlevallois.nocodefunctionswebservices.workflow.topics.TopicsEndPoint;
import net.clementlevallois.nocodefunctionswebservices.vvconversion.VosViewerConversionEndPoint;
import net.clementlevallois.nocodefunctionswebservices.workflow.communityinsights.CommunityInsightsEndPoint;
import net.clementlevallois.nocodefunctionswebservices.workflow.cowo.WorkflowCowoEndPoint;
import net.clementlevallois.umigon.classifier.controller.UmigonController;
import net.clementlevallois.umigon.model.classification.Document;
import net.clementlevallois.utils.Multiset;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class APIController {

    /**
     * @param args the command line arguments
     */
    private static Javalin app;
    public static String pwdOwner;
    public static Path tempFilesFolder;
    public static LLMsOps LLMOps;
    public static Globals globals;

    private static final Logger LOGGER = Logger.getLogger(APIController.class.getName());

    public static final ExecutorService backgroundExecutor = Executors.newCachedThreadPool();

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(HttpClient.Version.HTTP_2)
            .build();

    public static void main(String[] args) throws Exception {
        start();
    }

    private static void start() throws FileNotFoundException, IOException, Exception {

        Properties props = new Properties();
        props.load(new FileInputStream("private/props.properties"));
        String port = props.getProperty("port");

        boolean isLocal = System.getProperty("os.name").toLowerCase().contains("win");
        if (isLocal) {
            tempFilesFolder = Path.of(props.getProperty("pathToTempFilesWindows"));
        } else {
            tempFilesFolder = Path.of(props.getProperty("pathToTempFilesLinux"));
        }
        
        globals = new Globals(tempFilesFolder);

        app = Javalin.create(config -> {
            config.http.maxRequestSize = 1000000000;
        }).start(Integer.parseInt(port));

        pwdOwner = props.getProperty("pwdOwner");

        UmigonController umigonController = new UmigonController();
        SentimentEndPoints.initSentimentClassifiers(umigonController);
        OrganicEndPoints.initSentimentClassifiers(umigonController);

        LLMOps = new LLMsOps();

        app = SentimentEndPoints.addAll(app);
        app = OrganicEndPoints.addAll(app);
        app = PdfMatcherEndPoints.addAll(app);
        app = CowoEndPoint.addAll(app);
        app = WorkflowCowoEndPoint.addAll(app);
        app = LemmatizerLightEndPoint.addAll(app);
        app = TopicsEndPoint.addAll(app);
        app = LLMOpsEndpoints.addAll(app);
        app = CommunityInsightsEndPoint.addAll(app);
        app = GraphOpsEndPoint.addAll(app);
        app = GazeEndPoint.addAll(app);
        app = VosViewerConversionEndPoint.addAll(app);
        addRestartEndPoint();
        Runtime.getRuntime().addShutdownHook(new Thread(APIController::stopExecutorService));
        System.out.println("running the api");

    }

    public static void stopExecutorService() {
        LOGGER.info("Attempting to shut down background executor service...");
        backgroundExecutor.shutdown(); // Disable new tasks from being submitted
        try {
            if (!backgroundExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                LOGGER.warning("Executor did not terminate in 60 seconds, forcing shutdown...");
                backgroundExecutor.shutdownNow(); // Cancel currently executing tasks
                if (!backgroundExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    LOGGER.severe("Executor did not terminate even after forced shutdown.");
                } else {
                    LOGGER.info("Executor terminated after forced shutdown.");
                }
            } else {
                LOGGER.info("Executor terminated gracefully.");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            LOGGER.warning("Executor shutdown interrupted, forcing shutdown now.");
            backgroundExecutor.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    public static void addRestartEndPoint() {

        app.get("/api/restart", ctx -> {
            System.out.println("nocodefunctions api stopped at time " + LocalDateTime.now().toString());
            app.stop();
            start();
            System.out.println("nocodefunctions api restarted at time " + LocalDateTime.now().toString());
        });
    }

    public static void increment() {
        Long epochdays = LocalDate.now().toEpochDay();
        String message = epochdays.toString() + "\n";
        try {
            Files.write(Paths.get("api_calls.txt"), message.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("issue with the api call counter");
            System.out.println(e.getMessage());
        }
    }

    public static void sendProgressUpdate(int progress, String message, String callbackURL, String sessionId, String jobId) {
        if (callbackURL == null || callbackURL.isBlank()) {
            return;
        }
        JsonObjectBuilder joBuilder = Json.createObjectBuilder();
        joBuilder.add("info", "PROGRESS");
        joBuilder.add("function", WorkflowCowoProps.NAME);
        if (sessionId != null) {
            joBuilder.add("sessionId", sessionId);
        }
        joBuilder.add("dataPersistenceId", jobId);
        joBuilder.add("progress", progress);
        joBuilder.add("message", message != null ? message : "");
        sendCallback(joBuilder.build().toString(), callbackURL);
    }

    private static void sendCallback(String jsonPayload, String callbackURL) {
        try {
            URI uri = new URI(callbackURL);
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .build();

            try {
                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 300) {
                    LOGGER.log(Level.WARNING, "Callback POST failed");
                }
            } catch (HttpTimeoutException e) {
                LOGGER.log(Level.WARNING, "Callback POST timed out", e);
            } catch (ConnectException e) {
                LOGGER.log(Level.WARNING, "Callback POST connection refused", e);
            } catch (IOException | InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            }
        } catch (URISyntaxException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    public static byte[] byteArraySerializerForDocuments(Document o) {
        ObjectOutputStream oos = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(bos);
            oos.writeObject(o);
            oos.flush();
            byte[] data = bos.toByteArray();
            return data;
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        } finally {
            try {
                oos.close();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    public static byte[] byteArraySerializerForAnyObject(Object o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(o);
        oos.flush();
        byte[] data = bos.toByteArray();
        return data;
    }

    public static byte[] byteArraySerializerForListOfOccurrences(List<Occurrence> o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(o);
        oos.flush();
        byte[] data = bos.toByteArray();
        return data;
    }

    public static byte[] byteArraySerializerForTopics(Map<Integer, Multiset<String>> o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(o);
        oos.flush();
        byte[] data = bos.toByteArray();
        return data;
    }

    public static String turnJsonObjectToString(JsonObject jsonObject) {
        String output = "{}";
        try (java.io.StringWriter stringWriter = new StringWriter()) {
            var jsonWriter = Json.createWriter(stringWriter);
            jsonWriter.writeObject(jsonObject);
            output = stringWriter.toString();
        } catch (IOException ex) {
            Logger.getLogger(APIController.class.getName()).log(Level.SEVERE, null, ex);
        }
        return output;
    }

    public static String turnObjectToJsonString(Object o) {

        var jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(true));
        try (var writer = new StringWriter()) {
            jsonb.toJson(o, writer);
            return writer.toString();
        } catch (IOException ex) {
            System.out.println("exception when serializing object");
            System.out.println("object is: " + o.getClass());
            return "";
        }
    }

    public static <E extends Enum<E>> Optional<E> enumValueOf(Class<E> enumClass, String value) {
        try {
            return Optional.of(Enum.valueOf(enumClass, value.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

}
