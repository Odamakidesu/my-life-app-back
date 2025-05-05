package com.mylifeapp.component;


import com.mylifeapp.model.Tag;
import org.springframework.data.relational.core.mapping.event.BeforeSaveCallback;
import org.springframework.data.relational.core.conversion.MutableAggregateChange;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TagSaveCallback implements BeforeSaveCallback<Tag> {

    @Override
    public Tag onBeforeSave(Tag tag, MutableAggregateChange<Tag> aggregateChange) {
        LocalDateTime now = LocalDateTime.now();
        if (tag.getCreated_at() == null) {
            tag.setCreated_at(now);
        }
        tag.setUpdated_at(now);
        return tag;
    }
}