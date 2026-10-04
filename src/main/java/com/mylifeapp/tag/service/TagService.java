package com.mylifeapp.tag.service;

import com.mylifeapp.common.web.BadRequestException;
import com.mylifeapp.note.entity.Note;
import com.mylifeapp.note.repository.NoteRepository;
import com.mylifeapp.tag.dto.TagRequest;
import com.mylifeapp.tag.entity.Tag;
import com.mylifeapp.tag.exception.DuplicateTagNameException;
import com.mylifeapp.tag.exception.TagNotFoundException;
import com.mylifeapp.tag.repository.TagRepository;
import com.mylifeapp.tag.support.TagNames;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * タグのユースケース。
 *
 * <p>共通タグ（初期データ、user_id IS NULL）は読み取り専用。
 * 利用者が作ったタグは本人にだけ見え、本人だけが改名・色変更・削除できる。
 */
@Service
public class TagService {

    /** 名前だけで作られたタグの色（画面がタグの見つからない名前に使う既定色と同じ） */
    static final String DEFAULT_COLOR = "#6c757d";

    private final TagRepository tagRepository;
    private final NoteRepository noteRepository;
    private final Clock clock;

    public TagService(TagRepository tagRepository, NoteRepository noteRepository, Clock clock) {
        this.tagRepository = tagRepository;
        this.noteRepository = noteRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Tag> getVisibleTags(Long userId) {
        return tagRepository.findVisible(userId);
    }

    @Transactional
    public Tag create(Long userId, TagRequest request) {
        String name = request.name().trim();
        requireUniqueName(userId, name, -1L);

        Tag tag = new Tag();
        tag.setUserId(userId); // 所有者はリクエストではなく認証情報から決める
        tag.setName(name);
        tag.setColor(request.color());
        tag.setDeleteFlg(false);
        // created_at / updated_at は TagSaveCallback が採番する
        return tagRepository.save(tag);
    }

    /**
     * 改名・色変更。
     *
     * <p>メモとタグの対応は note_tags が ID で持つので、改名はタグの行を変えるだけでメモに反映される。
     * ただし旧版へ戻せるように書き続けている note.tags（名前の写し）も合わせて直す。
     * この写しは次のリリースで列ごと削除する。
     */
    @Transactional
    public Tag update(Long userId, Long id, TagRequest request) {
        Tag current = tagRepository.findOwned(id, userId)
                .orElseThrow(() -> new TagNotFoundException(id));
        String name = request.name().trim();
        requireUniqueName(userId, name, id);

        tagRepository.updateOwned(id, userId, name, request.color(), LocalDateTime.now(clock));

        if (!current.getName().equals(name)) {
            renameInNotes(userId, current.getName(), name);
        }
        return tagRepository.findOwned(id, userId)
                .orElseThrow(() -> new TagNotFoundException(id));
    }

    /**
     * 論理削除。メモに付いているタグ名はそのまま残す（色が既定色になるだけ）。
     * メモの内容を利用者の確認なしに書き換えないため。
     */
    @Transactional
    public void delete(Long userId, Long id) {
        if (tagRepository.softDeleteOwned(id, userId, LocalDateTime.now(clock)) == 0) {
            throw new TagNotFoundException(id);
        }
    }

    /**
     * メモに付けるタグ名（正規化済み）を、付けた順のタグ ID に解決する。
     *
     * <p>まだ無い名前は自分のタグとして既定色で作る。画面はタグの一覧から選ばせるので通常は起きないが、
     * API を直接使う場合や、以前の自由入力で付けた名前が残っている場合に、名前を黙って捨てないため。
     */
    @Transactional
    public List<Long> resolveForNote(Long userId, List<String> names) {
        List<Long> ids = new ArrayList<>(names.size());
        for (String name : names) {
            if (name.length() > TagNames.MAX_LENGTH) {
                throw new BadRequestException("タグ名は" + TagNames.MAX_LENGTH + "文字以内にしてください: " + name);
            }
            Tag tag = tagRepository.findResolvable(userId, name).orElseGet(() -> {
                Tag created = new Tag();
                created.setUserId(userId);
                created.setName(name);
                created.setColor(DEFAULT_COLOR);
                created.setDeleteFlg(false);
                return tagRepository.save(created);
            });
            ids.add(tag.getId());
        }
        return ids;
    }

    private void requireUniqueName(Long userId, String name, Long excludeId) {
        if (tagRepository.countVisibleByName(userId, name, excludeId) > 0) {
            throw new DuplicateTagNameException();
        }
    }

    private void renameInNotes(Long userId, String oldName, String newName) {
        for (Note note : noteRepository.findByUserIdAndTagsContaining(userId, oldName)) {
            String renamed = renameTag(note.getTags(), oldName, newName);
            if (!Objects.equals(renamed, note.getTags())) {
                noteRepository.updateTags(note.getId(), userId, renamed);
            }
        }
    }

    /**
     * カンマ区切りのタグ文字列の中で、oldName と完全一致する要素だけを newName に置き換える。
     * 置き換えの結果同じ名前が並んだ場合は1つにまとめる。
     */
    static String renameTag(String tags, String oldName, String newName) {
        if (tags == null) {
            return null;
        }
        Set<String> names = new LinkedHashSet<>();
        Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .map(tag -> tag.equals(oldName) ? newName : tag)
                .forEach(names::add);
        return String.join(",", names);
    }
}
