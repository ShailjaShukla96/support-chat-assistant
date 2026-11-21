package com.caramelai.support.knowledge;

import java.util.List;

/** Supplies the support content the assistant may answer from. v1 reads files; a vector store could replace it. */
public interface KnowledgeSource {

    List<SupportContentItem> items();
}
