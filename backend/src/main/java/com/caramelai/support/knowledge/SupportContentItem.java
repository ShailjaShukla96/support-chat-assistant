package com.caramelai.support.knowledge;

/** A FAQ, document, or troubleshooting guide. {@code id} is the file name without extension. */
public record SupportContentItem(String id, String title, String text) {}
