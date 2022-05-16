/*
 * author: Clément Levallois
 */
package net.clementlevallois.nocodefunctionswebservices.tweetretriever;

import com.twitter.clientlib.ApiException;
import com.twitter.clientlib.model.TweetSearchResponse;

/**
 *
 * @author LEVALLOIS
 */
public class ResponseFull {
    
    TweetSearchResponse response;
    ApiException apiException;

    public TweetSearchResponse getResponse() {
        return response;
    }

    public void setResponse(TweetSearchResponse response) {
        this.response = response;
    }

    public ApiException getApiException() {
        return apiException;
    }

    public void setApiException(ApiException apiException) {
        this.apiException = apiException;
    }
    
    
    
}
