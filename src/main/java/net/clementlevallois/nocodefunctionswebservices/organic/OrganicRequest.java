package net.clementlevallois.nocodefunctionswebservices.organic;

/**
 * A record to hold all parsed parameters for the Organic analysis request.
 * Records are concise, immutable data carriers.
 */
public record OrganicRequest(
    String owner,
    String text,
    Boolean shorter,
    String textLang,
    String outputFormat,
    String id,
    String explanation,
    String explanationLang
) {
}