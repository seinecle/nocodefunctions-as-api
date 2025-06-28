/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodefunctionswebservices.vvconversion;

/**
 *
 * @author clevallois
 */
public class GEXF2VVRequestParams {

    private String item;
    private String items;
    private String jobId;
    private String sessionId;
    private String callbackURL;
    private String link;
    private String links;
    private String linkStrength;
    private String totalLinkStrength;
    private String descriptionData;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCallbackURL() {
        return callbackURL;
    }

    public void setCallbackURL(String callbackURL) {
        this.callbackURL = callbackURL;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public String getItems() {
        return items;
    }

    public void setItems(String items) {
        this.items = items;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getLinks() {
        return links;
    }

    public void setLinks(String links) {
        this.links = links;
    }

    public String getLinkStrength() {
        return linkStrength;
    }

    public void setLinkStrength(String linkStrength) {
        this.linkStrength = linkStrength;
    }

    public String getTotalLinkStrength() {
        return totalLinkStrength;
    }

    public void setTotalLinkStrength(String totalLinkStrength) {
        this.totalLinkStrength = totalLinkStrength;
    }

    public String getDescriptionData() {
        return descriptionData;
    }

    public void setDescriptionData(String descriptionData) {
        this.descriptionData = descriptionData;
    }
    
    

}
