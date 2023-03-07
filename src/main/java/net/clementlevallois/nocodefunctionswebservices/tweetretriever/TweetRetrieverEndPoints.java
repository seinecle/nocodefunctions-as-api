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
import com.twitter.clientlib.TwitterCredentialsOAuth2;
import com.twitter.clientlib.api.TwitterApi;
import com.twitter.clientlib.auth.TwitterOAuth20Service;
import com.twitter.clientlib.model.Get2TweetsSearchRecentResponse;
import com.twitter.clientlib.model.Tweet;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import net.clementlevallois.nocodefunctionswebservices.APIController;
import static net.clementlevallois.nocodefunctionswebservices.APIController.increment;

/**
 *
 * @author LEVALLOIS
 */
public class TweetRetrieverEndPoints {

    public static void main(String[] args) throws FileNotFoundException, IOException {
        Properties props = new Properties();
        props.load(new FileInputStream("private/props.properties"));
        String twitterClientId = props.getProperty("twitter_client_id");
        String twitterClientSecret = props.getProperty("twitter_client_secret");

        TwitterCredentialsOAuth2 twitterApiOAuth2Credentials = new TwitterCredentialsOAuth2(twitterClientId,
                twitterClientSecret,
                "",
                "");

        TwitterOAuth20Service service = new TwitterOAuth20Service(
                twitterApiOAuth2Credentials.getTwitterOauth2ClientId(),
                twitterApiOAuth2Credentials.getTwitterOAuth2ClientSecret(),
                "https://nocodefunctions.com/twitter_auth.html",
                "offline.access tweet.read users.read");

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

            twitterApiOAuth2Credentials.setTwitterOauth2AccessToken(accessToken.getAccessToken());
            twitterApiOAuth2Credentials.setTwitterOauth2RefreshToken(accessToken.getRefreshToken());
            TwitterApi apiInstance = new TwitterApi(twitterApiOAuth2Credentials);

            ResponseFull responseFull = new TweetRetrieverEndPoints().getRecentTweets(apiInstance, "seinecle", 7, 0);

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

    public static Javalin addAll(Javalin app, TwitterCredentialsOAuth2 twitterApiOAuth2Credentials) throws Exception {

        app.get("/api/tweets/json", new Handler() {
            @Override
            public void handle(Context ctx) throws Exception {
                increment();
                //OAUTH2
                String accessToken = ctx.queryParam("accessToken");
                String refreshToken = ctx.queryParam("refreshToken");

                // QUERY ON RECENT TWEETS
                String query = ctx.queryParam("query");
                String daysStartParam = ctx.queryParam("days_start");
                Integer daysStart = Math.min(Integer.parseInt(daysStartParam), 7);
                String daysEndParam = ctx.queryParam("days_end");
                Integer daysEnd = Math.max(Integer.parseInt(daysEndParam), 0);

                twitterApiOAuth2Credentials.setTwitterOauth2AccessToken(accessToken);
                twitterApiOAuth2Credentials.setTwitterOauth2RefreshToken(refreshToken);
                TwitterApi twitterApiInstance = new TwitterApi(twitterApiOAuth2Credentials);


                ResponseFull responseFull = new TweetRetrieverEndPoints().getRecentTweets(twitterApiInstance, query, daysStart, daysEnd);
                if (responseFull.getResponse() == null) {
                    if (responseFull.getApiException() == null) {
                        ctx.result("{}").status(HttpURLConnection.HTTP_INTERNAL_ERROR);
                    } else {
                        long timetoWait = getTimeToWait(responseFull.getApiException());
                        ctx.result("{\"time to wait\":" + timetoWait + "}").status(HttpURLConnection.HTTP_BAD_REQUEST);
                    }
                } else {
                    Get2TweetsSearchRecentResponse recentTweets = responseFull.getResponse();
                    if (recentTweets.getErrors() == null) {
                        recentTweets.setErrors(new ArrayList());
                    }
                    if (recentTweets.getData() == null) {
                        recentTweets.setData(new ArrayList());
                    }
                    List<Tweet> tweets = recentTweets.getData();
                    String tweetsToJsonString = APIController.turnObjectToJsonString(tweets);
                    ctx.result(tweetsToJsonString).status(HttpURLConnection.HTTP_OK);
                }
            }
        });

        return app;
    }

    private ResponseFull getRecentTweets(TwitterApi apiInstance, String query, int daysStart, int daysEnd) {
        // Set the params values
        ResponseFull responseFull = new ResponseFull();
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
            Get2TweetsSearchRecentResponse  results = apiInstance.tweets().tweetsRecentSearch(query)
                    .startTime(startTime)
                    .endTime(endTime)
                    .sinceId(sinceId)
                    .untilId(untilId)
                    .maxResults(maxResults)
                    .sortOrder(sortOrder)
                    .nextToken(nextToken)
                    .paginationToken(paginationToken)
                    .expansions(expansions)
                    .tweetFields(tweetFields)
                    .userFields(userFields)
                    .mediaFields(mediaFields)
                    .placeFields(placeFields)
                    .pollFields(pollFields)
                    .execute();
            responseFull.setResponse(results);
        } catch (Exception e) {
            if (e instanceof ApiException) {
                ApiException eAPI = (ApiException) e;
                responseFull.setApiException(eAPI);
            }
            e.printStackTrace();
        }

        return responseFull;

    }

    static long getTimeToWait(ApiException e) {
        long timeToWait = 1000;

        if (isRateLimitRemaining(e)) {
            List<String> xRateLimitReset = e.getResponseHeaders().get("x-rate-limit-reset");
            if (xRateLimitReset != null && xRateLimitReset.get(0) != null) {
                timeToWait = Long.parseLong(
                        xRateLimitReset.get(0)) * 1000 - Calendar.getInstance().getTimeInMillis();
            }
        }
        return timeToWait;
    }

    static boolean isRateLimitRemaining(ApiException e) {
        List<String> xRateLimitRemaining = e.getResponseHeaders().get("x-rate-limit-remaining");
        return xRateLimitRemaining != null && xRateLimitRemaining.get(0) != null
                && Long.parseLong(xRateLimitRemaining.get(0)) == 0;
    }
}
