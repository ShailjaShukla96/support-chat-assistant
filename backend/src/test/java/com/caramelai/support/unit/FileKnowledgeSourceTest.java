package com.caramelai.support.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caramelai.support.knowledge.FileKnowledgeSource;
import com.caramelai.support.knowledge.SupportContentItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileKnowledgeSourceTest {

    @TempDir Path dir;

    private String location() {
        return dir.toUri().toString();
    }

    @Test
    void loadsMarkdownFilesWithIdAndTitle() throws Exception {
        Files.writeString(dir.resolve("rate-limits.md"), "# Rate limits\nYou get 429 when over quota.");
        Files.writeString(dir.resolve("no-heading.md"), "Just text without a heading.");
        Files.writeString(dir.resolve("ignored.txt"), "not markdown");

        List<SupportContentItem> items = new FileKnowledgeSource(location(), 60000).items();

        assertThat(items).hasSize(2);
        SupportContentItem rate = items.stream().filter(i -> i.id().equals("rate-limits")).findFirst().orElseThrow();
        assertThat(rate.title()).isEqualTo("Rate limits");
        assertThat(rate.text()).contains("429");
        SupportContentItem plain = items.stream().filter(i -> i.id().equals("no-heading")).findFirst().orElseThrow();
        assertThat(plain.title()).isEqualTo("no-heading");
    }

    @Test
    void startupFailsWhenTotalSizeExceedsTheCap() throws Exception {
        Files.writeString(dir.resolve("a.md"), "x".repeat(600));
        Files.writeString(dir.resolve("b.md"), "y".repeat(600));

        assertThatThrownBy(() -> new FileKnowledgeSource(location(), 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1000");
    }

    @Test
    void acceptsContentExactlyAtTheCap() throws Exception {
        Files.writeString(dir.resolve("a.md"), "x".repeat(1000));
        assertThat(new FileKnowledgeSource(location(), 1000).items()).hasSize(1);
    }
}
