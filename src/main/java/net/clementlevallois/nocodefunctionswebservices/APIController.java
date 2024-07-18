/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices;

import io.javalin.Javalin;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.Occurrence;
import net.clementlevallois.nocodefunctionswebservices.bibliocoupling.BibliocouplingEndPoints;
import net.clementlevallois.nocodefunctionswebservices.cowo.CowoEndPoint;
import net.clementlevallois.nocodefunctionswebservices.sentiment.SentimentEndPoints;
import net.clementlevallois.nocodefunctionswebservices.gaze.GazeEndPoint;
import net.clementlevallois.nocodefunctionswebservices.graphops.GraphOpsEndPoint;
import net.clementlevallois.nocodefunctionswebservices.lemmatizerlight.LemmatizerLightEndPoint;
import net.clementlevallois.nocodefunctionswebservices.linkprediction.LinkPredictionEndPoint;
import net.clementlevallois.nocodefunctionswebservices.organic.OrganicEndPoints;
import net.clementlevallois.nocodefunctionswebservices.pdfmatcher.PdfMatcherEndPoints;
import net.clementlevallois.nocodefunctionswebservices.spatialize.SpatializeEndPoint;
import net.clementlevallois.nocodefunctionswebservices.topics.TopicsEndPoint;
import net.clementlevallois.nocodefunctionswebservices.vvconversion.VosViewerConversionEndPoint;
import net.clementlevallois.umigon.classifier.controller.UmigonController;
import net.clementlevallois.umigon.model.classification.Document;
import net.clementlevallois.utils.Multiset;

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

        app = Javalin.create(config -> {
            config.http.maxRequestSize = 1000000000;
        }).start(Integer.parseInt(port));

        pwdOwner = props.getProperty("pwdOwner");

        UmigonController umigonController = new UmigonController();
        SentimentEndPoints.initSentimentClassifiers(umigonController);
        OrganicEndPoints.initSentimentClassifiers(umigonController);
        app = SentimentEndPoints.addAll(app);
        app = OrganicEndPoints.addAll(app);
        app = PdfMatcherEndPoints.addAll(app);
        app = CowoEndPoint.addAll(app);
        app = LemmatizerLightEndPoint.addAll(app);
        app = TopicsEndPoint.addAll(app);
        app = GraphOpsEndPoint.addAll(app);
        app = LinkPredictionEndPoint.addAll(app);
        app = GazeEndPoint.addAll(app);
        app = VosViewerConversionEndPoint.addAll(app);
        app = SpatializeEndPoint.addAll(app);
        app = BibliocouplingEndPoints.addAll(app);
        addRestartEndPoint();
        System.out.println("running the api");

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

    public static byte[] byteArraySerializerForDocuments(Document o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(o);
        oos.flush();
        byte[] data = bos.toByteArray();
        return data;
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

}
