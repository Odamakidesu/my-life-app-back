package com.mylifeapp.tag.support;

import com.mylifeapp.tag.model.Tag;
import org.springframework.data.relational.core.conversion.MutableAggregateChange;
import org.springframework.data.relational.core.mapping.event.BeforeSaveCallback;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class TagSaveCallback implements BeforeSaveCallback<Tag> {

    private final Clock clock;

    public TagSaveCallback(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Tag onBeforeSave(Tag tag, MutableAggregateChange<Tag> aggregateChange) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (tag.getCreatedAt() == null) {
            tag.setCreatedAt(now);
        }
        tag.setUpdatedAt(now);
        return tag;
    }
}
