/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodefunctionswebservices.tweetretriever;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.pkce.PKCE;
import com.github.scribejava.core.pkce.PKCECodeChallengeMethod;
import com.twitter.clientlib.ApiException;
import com.twitter.clientlib.TwitterCredentialsBearer;
import com.twitter.clientlib.TwitterCredentialsOAuth2;
import com.twitter.clientlib.api.TwitterApi;
import com.twitter.clientlib.auth.TwitterOAuth20Service;
import com.twitter.clientlib.model.Problem;
import com.twitter.clientlib.model.Tweet;
import com.twitter.clientlib.model.TweetSearchResponse;
import io.javalin.Javalin;
import io.javalin.http.HttpCode;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import static net.clementlevallois.nocodefunctionswebservices.APIController.increment;

/**
 *
 * @author LEVALLOIS
 */
public class TweetRetrieverEndPoints {

    public static void main(String[] args) throws FileNotFoundException, IOException {
        Properties props = new Properties();
        props.load(new FileInputStream("private/props.properties"));

        TwitterCredentialsOAuth2 credentials = new TwitterCredentialsOAuth2(props.getProperty("twitter_client_id"),
                props.getProperty("twitter_client_secret"),
                props.getProperty("twitter_access_token"),
                System.getenv("TWITTER_OAUTH2_REFRESH_TOKEN")) // absent
                ;

        TwitterOAuth20Service service = new TwitterOAuth20Service(
                credentials.getTwitterOauth2ClientId(),
                credentials.getTwitterOAuth2ClientSecret(),
                "http://twitter.com",
                "offline.access tweet.read");

        OAuth2AccessToken accessToken = null;
        try {
            final Scanner in = new Scanner(System.in, "UTF-8");
            System.out.println("Fetching the Authorization URL...");

            final String secretState = "state";
            PKCE pkce = new PKCE();
            pkce.setCodeChallenge("challenge");
            pkce.setCodeChallengeMethod(PKCECodeChallengeMethod.PLAIN);
            pkce.setCodeVerifier("challenge");
            String authorizationUrl = service.getAuthorizationUrl(pkce, secretState);

            System.out.println("Go to the Authorization URL and authorize your App:\n"
                    + authorizationUrl + "\nAfter that paste the authorization code here\n>>");
            final String code = in.nextLine();
            System.out.println("\nTrading the Authorization Code for an Access Token...");
            accessToken = service.getAccessToken(pkce, code);

            System.out.println("Access token: " + accessToken.getAccessToken());
            System.out.println("Refresh token: " + accessToken.getRefreshToken());
        } catch (Exception e) {
            System.err.println("Error while getting the access token:\n " + e);
            e.printStackTrace();
        }
//        TwitterCredentialsBearer credentials = new TwitterCredentialsBearer(props.getProperty("twitter-token-bearer"));
//        TwitterApi apiInstance = new TwitterApi();
//        apiInstance.setTwitterCredentials(credentials);
//        String query = "(from:TwitterDev OR from:TwitterAPI) -is:retweet"; // String | One query/rule/filter for matching Tweets. Refer to https://t.co/rulelength to identify the max query length
//        String query = "seinecle"; // String | One query/rule/filter for matching Tweets. Refer to https://t.co/rulelength to identify the max query length
//        TweetSearchResponse recentTweets = new TweetRetrieverEndPoints().getRecentTweets(apiInstance, query, 7, 0);
//
//        List<Tweet> tweets = recentTweets.getData();
//        if (tweets != null) {
//            for (Tweet tweet : tweets) {
//                System.out.println("tweet: " + tweet.getText());
//            }
//        }
//        List<Problem> errors = recentTweets.getErrors();
//        if (errors != null) {
//            for (Problem problem : errors) {
//                System.out.println("problem: " + problem.getTitle());
//            }
//        }
//
//        String json = recentTweets.toJson();
//        TweetSearchResponse fromJson = TweetSearchResponse.fromJson(json);

    }

    public static Javalin addAll(Javalin app, TwitterApi apiInstance) throws Exception {

        app.get("/api/tweets/bytes", ctx -> {
            increment();
            String query = ctx.queryParam("query");
            String daysStartParam = ctx.queryParam("days_start");
            Integer daysStart = Math.min(Integer.valueOf(daysStartParam), 7);
            String daysEndParam = ctx.queryParam("days_end");
            Integer daysEnd = Math.max(Integer.valueOf(daysEndParam), 0);

            TweetSearchResponse recentTweets = new TweetRetrieverEndPoints().getRecentTweets(apiInstance, query, daysStart, daysEnd);
            if (recentTweets.getErrors() == null) {
                recentTweets.setErrors(new ArrayList<Problem>());
            }
            if (recentTweets.getData() == null) {
                recentTweets.setData(new ArrayList<Tweet>());
            }
            ctx.result(recentTweets.toJson()).status(HttpCode.OK);

        });

        return app;
    }

    private TweetSearchResponse getRecentTweets(TwitterApi apiInstance, String query, int daysStart, int daysEnd) {
        // Set the params values
        TweetSearchResponse result = null;
        OffsetDateTime startTime = OffsetDateTime.now().minus(Duration.ofDays(daysStart)); // OffsetDateTime | YYYY-MM-DDTHH:mm:ssZ. The oldest UTC timestamp (from most recent 7 days) from which the Tweets will be provided. Timestamp is in second granularity and is inclusive (i.e. 12:00:01 includes the first second of the minute).
        OffsetDateTime endTime = OffsetDateTime.now().minus(Duration.ofDays(daysEnd)).minus(Duration.ofSeconds(11)); // OffsetDateTime | YYYY-MM-DDTHH:mm:ssZ. The newest, most recent UTC timestamp to which the Tweets will be provided. Timestamp is in second granularity and is exclusive (i.e. 12:00:01 excludes the first second of the minute).
        String sinceId = null; // String | Returns results with a Tweet ID greater than (that is, more recent than) the specified ID.
        String untilId = null; // String | Returns results with a Tweet ID less than (that is, older than) the specified ID.
        Integer maxResults = 100; // Integer | The maximum number of search results to be returned by a request.
        String sortOrder = "recency"; // String | This order in which to return results.
        String nextToken = null; // String | This parameter is used to get the next 'page' of results. The value used with the parameter is pulled directly from the response provided by the API, and should not be modified.
        String paginationToken = null; // String | This parameter is used to get the next 'page' of results. The value used with the parameter is pulled directly from the response provided by the API, and should not be modified.
        Set<String> expansions = new HashSet<>(Arrays.asList()); // Set<String> | A comma separated list of fields to expand.
        Set<String> tweetFields = Set.of("id", "created_at", "text", "author_id", "in_reply_to_user_id", "referenced_tweets", "attachments", "withheld", "geo", "entities", "public_metrics", "possibly_sensitive", "source", "lang", "context_annotations", "conversation_id", "reply_settings");
        Set<String> userFields = Set.of("id", "created_at", "name", "username", "protected", "verified", "withheld", "profile_image_url", "location", "url", "description", "entities", "pinned_tweet_id", "public_metrics"); // Set<String> | A comma separated list of User fields to display.
        Set<String> mediaFields = new HashSet<>(Arrays.asList()); // Set<String> | A comma separated list of Media fields to display.
        Set<String> placeFields = Set.of("id", "name", "country_code", "place_type", "full_name", "country", "contained_within", "geo"); // Set<String> | A comma separated list of Place fields to display.
        Set<String> pollFields = new HashSet<>(Arrays.asList()); // Set<String> | A comma separated list of Poll fields to display.
        try {
            result = apiInstance.tweets().tweetsRecentSearch(query, startTime, endTime, sinceId, untilId, maxResults, sortOrder, nextToken, paginationToken, expansions, tweetFields, userFields, mediaFields, placeFields, pollFields);
        } catch (ApiException e) {
            System.err.println("Exception when calling TweetsApi#tweetsRecentSearch");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }

        return result;

    }
}
