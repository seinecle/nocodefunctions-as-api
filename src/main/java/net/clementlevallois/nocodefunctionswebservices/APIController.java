/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices;

import com.twitter.clientlib.TwitterCredentialsBearer;
import com.twitter.clientlib.api.TwitterApi;
import com.twitter.clientlib.model.TweetSearchResponse;
import io.javalin.Javalin;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import net.clementlevallois.nocodefunctionswebservices.sentiment.SentimentEndPoints;
import net.clementlevallois.nocodefunctionswebservices.delight.DelightEndPoints;
import net.clementlevallois.nocodefunctionswebservices.organic.OrganicEndPoints;
import net.clementlevallois.nocodefunctionswebservices.pdfmatcher.PdfMatcherEndPoints;
import net.clementlevallois.nocodefunctionswebservices.tweetretriever.TweetRetrieverEndPoints;
import net.clementlevallois.pdfmatcher.controller.Occurrence;
import net.clementlevallois.umigon.controller.UmigonController;
import net.clementlevallois.umigon.model.Document;

/**
 *
 * @author LEVALLOIS
 */
public class APIController {

    /**
     * @param args the command line arguments
     */
    private static Javalin app = Javalin.create().start(7002);
    private static TwitterApi apiInstance;
    public static String pwdOwner ;

    public static void main(String[] args) throws Exception {
        UmigonController umigonController = new UmigonController();
        Properties props = new Properties();
        props.load(new FileInputStream("private/props.properties"));
        pwdOwner = props.getProperty("pwdOwner");

        TwitterCredentialsBearer credentials = new TwitterCredentialsBearer(props.getProperty("twitter-token-bearer"));
        apiInstance = new TwitterApi();
        apiInstance.setTwitterCredentials(credentials);

        app = SentimentEndPoints.addAll(app, umigonController);
        app = OrganicEndPoints.addAll(app, umigonController);
        app = DelightEndPoints.addAll(app, umigonController);
        app = PdfMatcherEndPoints.addAll(app);
        app = TweetRetrieverEndPoints.addAll(app, apiInstance);
        System.out.println("running the api");

    }

    public static void increment() {
        Long epochdays = LocalDate.now().toEpochDay();
        String message = epochdays.toString() + "\n";
        try {
            Files.write(Paths.get("api_calls.txt"), message.getBytes(), StandardOpenOption.APPEND);
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

    public static byte[] byteArraySerializerForTweets(TweetSearchResponse o) throws IOException {
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

}
