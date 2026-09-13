package com.mylifeapp.note.repository;

import com.mylifeapp.note.model.Note;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends CrudRepository<Note, Long> {
    // ここにカスタムクエリを追加
    @Query("SELECT * FROM note WHERE delete_flg = false")
    List<Note> findAllActiveNotes();
}