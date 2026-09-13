package com.mylifeapp.tag.service;

import com.mylifeapp.tag.model.Tag;
import com.mylifeapp.tag.repository.TagRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public List<Tag> getAllTags() {
        return StreamSupport
                .stream(tagRepository.findAll().spliterator(), false)
                .collect(Collectors.toList());
    }
}