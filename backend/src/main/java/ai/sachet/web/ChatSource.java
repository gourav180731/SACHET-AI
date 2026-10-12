package ai.sachet.web;

/** Source citation for a chat answer. */
public record ChatSource(
    String documentId,
    String documentTitle,
    int documentVersion,
    int page,
    String section,
    double vectorScore
) {
}