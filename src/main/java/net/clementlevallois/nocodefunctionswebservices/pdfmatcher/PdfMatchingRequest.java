package net.clementlevallois.nocodefunctionswebservices.pdfmatcher;

import java.util.TreeMap;

/**
 * A record to hold all parsed parameters for the PDF matching request.
 */
public record PdfMatchingRequest(
    TreeMap<Integer, String> lines,
    TreeMap<Integer, Integer> pages,
    Integer nbWords,
    Integer nbLines,
    String searchedTerm,
    Boolean caseSensitive,
    String endOfPage,
    String startOfPage,
    String typeOfContext,
    String fileName
) {
}