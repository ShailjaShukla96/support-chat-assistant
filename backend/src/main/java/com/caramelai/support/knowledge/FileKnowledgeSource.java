package com.caramelai.support.knowledge;

import com.caramelai.support.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Reads every {@code *.md} file at the configured location once, at startup. Fails startup when the total size
 * exceeds the cap, because all content goes into the prompt (research R6).
 */
@Component
public class FileKnowledgeSource implements KnowledgeSource {

    private final List<SupportContentItem> items;

    @Autowired
    public FileKnowledgeSource(AppProperties props) {
        this(props.knowledge().path(), props.knowledge().maxChars());
    }

    public FileKnowledgeSource(String location, int maxChars) {
        String base = location.endsWith("/") ? location : location + "/";
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(base + "*.md");
            this.items = Arrays.stream(resources)
                    .sorted(Comparator.comparing(r -> String.valueOf(r.getFilename())))
                    .map(FileKnowledgeSource::toItem)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read support content at " + location, e);
        }
        int total = items.stream().mapToInt(i -> i.text().length()).sum();
        if (total > maxChars) {
            throw new IllegalStateException("Support content is " + total
                    + " characters, over the limit of " + maxChars + " (app.knowledge.max-chars).");
        }
    }

    private static SupportContentItem toItem(Resource resource) {
        try {
            String fileName = resource.getFilename();
            String id = fileName.substring(0, fileName.length() - ".md".length());
            String text = resource.getContentAsString(StandardCharsets.UTF_8);
            return new SupportContentItem(id, titleOf(text, id), text);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }

    private static String titleOf(String text, String fallback) {
        for (String line : text.split("\\R")) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        return fallback;
    }

    @Override
    public List<SupportContentItem> items() {
        return items;
    }
}
