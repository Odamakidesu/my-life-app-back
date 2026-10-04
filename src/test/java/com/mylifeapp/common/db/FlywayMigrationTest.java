package com.mylifeapp.common.db;

import com.mylifeapp.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway の適用経路。
 *
 * <p>本番と既存のローカル Docker の DB は Flyway 導入前から存在し、履歴テーブルが無い。
 * それらが起動時に「V1 を適用済みとみなし、V2 以降だけを流す」ことで追いつけることを、
 * 同じコンテナ内に別のデータベースを作って再現する。
 */
class FlywayMigrationTest extends AbstractIntegrationTest {

    private static final String MIGRATION = "classpath:db/migration";
    private static final String SEED = "classpath:db/seed";

    /** application.properties と同じ設定で Flyway を組み立てる。 */
    private static Flyway flyway(DataSource dataSource, String... locations) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .encoding("UTF-8")
                .load();
    }

    /** Flyway 導入前の DB（V1 のスキーマがあり、履歴テーブルが無い）を作る。 */
    private DataSource legacyDatabase(String name) {
        JdbcTemplate root = new JdbcTemplate(rootDataSource(""));
        root.execute("DROP DATABASE IF EXISTS " + name);
        root.execute("CREATE DATABASE " + name + " CHARACTER SET utf8mb4");

        DataSource dataSource = rootDataSource(name);
        Flyway.configure().dataSource(dataSource).locations(MIGRATION).target("1").load().migrate();
        JdbcTemplate db = new JdbcTemplate(dataSource);
        db.execute("DROP TABLE flyway_schema_history");
        db.update("""
                INSERT INTO tags (name, color, created_at, updated_at, delete_flg)
                VALUES ('既存タグ', '#000000', NOW(), NOW(), false)
                """);
        return dataSource;
    }

    private static int columnCount(JdbcTemplate db, String table, String column) {
        return db.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
    }

    @Test
    @DisplayName("このアプリの DB は最新の版まで適用されている")
    void applicationDatabaseIsUpToDate() {
        Integer failed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false", Integer.class);
        assertThat(failed).isZero();
        assertThat(columnCount(jdbcTemplate, "tags", "user_id")).isOne();
    }

    @Test
    @DisplayName("Flyway 導入前の DB は V1 をベースラインとし、V2 だけが流れて既存データは残る")
    void legacyDatabaseCatchesUpFromBaseline() {
        DataSource dataSource = legacyDatabase("flyway_legacy");

        MigrateResult result = flyway(dataSource, MIGRATION).migrate();

        JdbcTemplate db = new JdbcTemplate(dataSource);
        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(columnCount(db, "tags", "user_id")).isOne();
        assertThat(db.queryForObject(
                "SELECT COUNT(*) FROM tags WHERE name = '既存タグ' AND user_id IS NULL", Integer.class))
                .isOne();
    }

    @Test
    @DisplayName("V2 を手作業で適用済みの DB でも失敗せずに追いつく")
    void legacyDatabaseWithManualV2DoesNotFail() {
        DataSource dataSource = legacyDatabase("flyway_manual_v2");
        new JdbcTemplate(dataSource).execute("""
                ALTER TABLE tags
                    ADD COLUMN user_id BIGINT NULL AFTER id,
                    ADD CONSTRAINT fk_tags_user FOREIGN KEY (user_id) REFERENCES users (id),
                    ADD INDEX idx_tags_user_active (user_id, delete_flg)
                """);

        flyway(dataSource, MIGRATION).migrate();

        assertThat(columnCount(new JdbcTemplate(dataSource), "tags", "user_id")).isOne();
    }

    @Test
    @DisplayName("開発用シードは何度流しても重複しない")
    void seedIsIdempotent() {
        DataSource dataSource = legacyDatabase("flyway_seed");
        JdbcTemplate db = new JdbcTemplate(dataSource);
        flyway(dataSource, MIGRATION, SEED).migrate();
        long tags = db.queryForObject("SELECT COUNT(*) FROM tags", Long.class);
        long notes = db.queryForObject("SELECT COUNT(*) FROM note", Long.class);
        long users = db.queryForObject("SELECT COUNT(*) FROM users", Long.class);

        // 繰り返し可能マイグレーションの記録を消して、もう一度流させる（内容を変えた場合と同じ）
        db.update("DELETE FROM flyway_schema_history WHERE version IS NULL");
        flyway(dataSource, MIGRATION, SEED).migrate();

        assertThat(db.queryForObject("SELECT COUNT(*) FROM tags", Long.class)).isEqualTo(tags);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM note", Long.class)).isEqualTo(notes);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(users);
        assertThat(users).isEqualTo(2);
        assertThat(notes).isEqualTo(10);
    }
}
