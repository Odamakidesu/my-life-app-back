package com.mylifeapp.tag.service;

import com.mylifeapp.tag.entity.Tag;
import com.mylifeapp.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Transactional(readOnly = true)
    public List<Tag> getActiveTags() {
        return tagRepository.findActive();
    }
}
