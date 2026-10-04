package com.mylifeapp.tag.controller;

import com.mylifeapp.auth.userdetails.UserPrincipal;
import com.mylifeapp.tag.dto.TagRequest;
import com.mylifeapp.tag.dto.TagResponse;
import com.mylifeapp.tag.service.TagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /** 共通タグと自分のタグ */
    @GetMapping
    public List<TagResponse> getAllTags(@AuthenticationPrincipal UserPrincipal principal) {
        return tagService.getVisibleTags(principal.getUserId()).stream()
                .map(TagResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<TagResponse> createTag(@AuthenticationPrincipal UserPrincipal principal,
                                                 @Valid @RequestBody TagRequest request) {
        TagResponse created = TagResponse.from(tagService.create(principal.getUserId(), request));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public TagResponse updateTag(@AuthenticationPrincipal UserPrincipal principal,
                                 @PathVariable Long id,
                                 @Valid @RequestBody TagRequest request) {
        return TagResponse.from(tagService.update(principal.getUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable Long id) {
        tagService.delete(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
