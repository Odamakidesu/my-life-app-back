package com.mylifeapp.controller;

import com.mylifeapp.model.Note;
import com.mylifeapp.repository.NoteRepository;
import com.mylifeapp.dto.ImportantUpdateRequest;
import com.mylifeapp.dto.PinnedUpdateRequest;
import com.mylifeapp.dto.CompletedUpdateRequest;
import com.mylifeapp.dto.DeletedUpdateRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NoteController {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private final NoteRepository noteRepository;
    @Autowired
    public NoteController(JdbcTemplate jdbcTemplate, NoteRepository noteRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.noteRepository = noteRepository;
    }
//    public NoteController(NoteRepository noteRepository) {
//        this.noteRepository = noteRepository;
//    }
//    @GetMapping
//    public List<String> getNotes() {
//        return List.of("メモ1", "メモ2");
//    }
    // GET /api/notes：すべてのメモを取得
    @GetMapping
    public List<Note> getAllNotes() {
        return noteRepository.findAllActiveNotes();
        // repositoryにカスタムクエリを作成
//        return StreamSupport.stream(noteRepository.findAll().spliterator(), false)
//                .collect(Collectors.toList());
    }

    // POST /api/notes：メモを追加
    @PostMapping
    public Note createNote(@RequestBody Note note) {
        return noteRepository.save(note);
    }

    // DELETE /api/notes/{id}：メモを削除
    // 物理削除から論理削除に変更の過程で未使用になる。
    @DeleteMapping("/{id}")
    public void deleteNote(@PathVariable Long id) {
        noteRepository.deleteById(id);
    }

    @PutMapping("/{id}")
    public Note updateNote(@PathVariable Long id, @RequestBody Note updatedNote) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("メモが見つかりませんでした: " + id));
        note.setTitle(updatedNote.getTitle());
        note.setContent(updatedNote.getContent());
        note.setTags(updatedNote.getTags());
        note.setDeadline(updatedNote.getDeadline());
        return noteRepository.save(note);
    }

    @PutMapping("/{id}/important")
    public ResponseEntity<Void> updateImportant(@PathVariable Long id, @RequestBody ImportantUpdateRequest request) {
        jdbcTemplate.update(
                "UPDATE note SET is_important = ? WHERE id = ?",
                request.getImportant(), id
        );

        return ResponseEntity.ok().build();
    }
    @PutMapping("/{id}/pinned")
    public ResponseEntity<Void> updatePinned(@PathVariable Long id, @RequestBody PinnedUpdateRequest request) {
        jdbcTemplate.update(
                "UPDATE note SET is_pinned = ? WHERE id = ?",
                request.getPinned(), id
        );
        return ResponseEntity.ok().build();
    }

    // メモを完了済にする
    @PutMapping("/{id}/completed")
    public ResponseEntity<Void> updateCompleted(@PathVariable Long id, @RequestBody CompletedUpdateRequest request) {
        jdbcTemplate.update(
                "UPDATE note SET is_completed= ? WHERE id = ?",
                request.getCompleted(), id
        );
        return ResponseEntity.ok().build();
    }

    // DELETE /api/notes/{id}：メモを論理削除
    @PutMapping("/{id}/deleted")
    public ResponseEntity<Void> updateNoteDelete(@PathVariable Long id, @RequestBody DeletedUpdateRequest request) {
        jdbcTemplate.update(
                "UPDATE note SET delete_flg = ? WHERE id = ?",
                request.getDelete_flg(), id
        );

        return ResponseEntity.ok().build();
    }
}