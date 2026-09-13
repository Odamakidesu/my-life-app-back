package com.mylifeapp.note.controller;

import com.mylifeapp.auth.userdetails.UserPrincipal;
import com.mylifeapp.note.dto.CompletedUpdateRequest;
import com.mylifeapp.note.dto.DeletedUpdateRequest;
import com.mylifeapp.note.dto.ImportantUpdateRequest;
import com.mylifeapp.note.dto.NoteCreateRequest;
import com.mylifeapp.note.dto.NoteResponse;
import com.mylifeapp.note.dto.NoteUpdateRequest;
import com.mylifeapp.note.dto.PinnedUpdateRequest;
import com.mylifeapp.note.service.NoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    /**
     * 自分のメモ一覧。
     *
     * <p>レスポンスは従来どおり JSON 配列のまま返し、総件数は X-Total-Count ヘッダに載せる。
     * オブジェクトで包むと既存のフロントエンドが壊れるため。
     */
    @GetMapping
    public ResponseEntity<List<NoteResponse>> getAllNotes(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + NoteService.DEFAULT_PAGE_SIZE) int size) {

        Long userId = principal.getUserId();
        List<NoteResponse> body = noteService.findActive(userId, page, size).stream()
                .map(NoteResponse::from)
                .toList();

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(noteService.countActive(userId)))
                .body(body);
    }

    @PostMapping
    public ResponseEntity<NoteResponse> createNote(@AuthenticationPrincipal UserPrincipal principal,
                                                   @Valid @RequestBody NoteCreateRequest request) {
        NoteResponse created = NoteResponse.from(noteService.create(principal.getUserId(), request));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public NoteResponse updateNote(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable Long id,
                                   @Valid @RequestBody NoteUpdateRequest request) {
        return NoteResponse.from(noteService.update(principal.getUserId(), id, request));
    }

    @PutMapping("/{id}/important")
    public ResponseEntity<Void> updateImportant(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable Long id,
                                                @Valid @RequestBody ImportantUpdateRequest request) {
        noteService.setImportant(principal.getUserId(), id, request.important());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/pinned")
    public ResponseEntity<Void> updatePinned(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id,
                                             @Valid @RequestBody PinnedUpdateRequest request) {
        noteService.setPinned(principal.getUserId(), id, request.pinned());
        return ResponseEntity.ok().build();
    }

    /** メモを完了済にする */
    @PutMapping("/{id}/completed")
    public ResponseEntity<Void> updateCompleted(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable Long id,
                                                @Valid @RequestBody CompletedUpdateRequest request) {
        noteService.setCompleted(principal.getUserId(), id, request.completed());
        return ResponseEntity.ok().build();
    }

    /** メモを論理削除する */
    @PutMapping("/{id}/deleted")
    public ResponseEntity<Void> updateNoteDelete(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody DeletedUpdateRequest request) {
        noteService.setDeleted(principal.getUserId(), id, request.deleteFlg());
        return ResponseEntity.ok().build();
    }
}
