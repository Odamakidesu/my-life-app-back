package com.mylifeapp.note.service;

import com.mylifeapp.common.web.BadRequestException;
import com.mylifeapp.note.dto.NoteResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * メモのエクスポート（JSON / CSV）。
 *
 * <p>JSON は一覧 API と同じ形の配列なので、そのまま読み戻せる。
 * CSV は表計算ソフトで開く前提で、Excel が文字コードを判別できるよう UTF-8 の BOM を付ける。
 */
@Component
public class NoteExporter {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter CSV_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final List<String> CSV_HEADER = List.of(
            "id", "title", "content", "tags", "important", "pinned", "completed",
            "deadline", "recurrence", "created_at");

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NoteExporter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ResponseEntity<byte[]> export(List<NoteResponse> notes, String format) {
        String baseName = "mylifeapp-notes-" + LocalDate.now(clock).format(FILE_DATE);
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "json" -> file(objectMapper.writeValueAsBytes(notes),
                    MediaType.APPLICATION_JSON, baseName + ".json");
            case "csv" -> file(toCsv(notes), new MediaType("text", "csv", StandardCharsets.UTF_8),
                    baseName + ".csv");
            default -> throw new BadRequestException("format は json か csv を指定してください。");
        };
    }

    static byte[] toCsv(List<NoteResponse> notes) {
        StringBuilder csv = new StringBuilder();
        appendRow(csv, CSV_HEADER);
        for (NoteResponse note : notes) {
            appendRow(csv, List.of(
                    String.valueOf(note.id()),
                    nullToEmpty(note.title()),
                    nullToEmpty(note.content()),
                    nullToEmpty(note.tags()),
                    String.valueOf(Boolean.TRUE.equals(note.isImportant())),
                    String.valueOf(Boolean.TRUE.equals(note.isPinned())),
                    String.valueOf(Boolean.TRUE.equals(note.isCompleted())),
                    format(note.deadline()),
                    note.recurrence() == null ? "" : note.recurrence().name(),
                    format(note.createdAt())));
        }
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, withBom, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, withBom, UTF8_BOM.length, body.length);
        return withBom;
    }

    private static void appendRow(StringBuilder csv, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(cells.get(i)));
        }
        csv.append("\r\n");
    }

    /**
     * RFC 4180 の引用に加え、表計算ソフトの数式として解釈される先頭文字（= + - @ タブ CR）を無害化する。
     * メモの本文は利用者が自由に書けるので、そのまま出すと開いた人の環境で数式が実行され得る（CSV インジェクション）。
     */
    static String escape(String value) {
        String cell = value;
        if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0) {
            cell = "'" + cell;
        }
        if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
            cell = "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }

    private static String format(LocalDateTime value) {
        return value == null ? "" : value.format(CSV_DATE_TIME);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static ResponseEntity<byte[]> file(byte[] body, MediaType type, String fileName) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }
}
