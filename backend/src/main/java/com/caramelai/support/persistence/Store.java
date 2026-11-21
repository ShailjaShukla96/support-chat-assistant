package com.caramelai.support.persistence;

import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Component;

/**
 * Inserts and updates entities that carry application-generated UUIDs. {@code CrudRepository.save} would treat
 * a non-null ID as an update, so inserts go through the template explicitly.
 */
@Component
public class Store {

    private final JdbcAggregateTemplate template;

    public Store(JdbcAggregateTemplate template) {
        this.template = template;
    }

    public <T> T insert(T entity) {
        return template.insert(entity);
    }

    public <T> T update(T entity) {
        return template.update(entity);
    }
}
