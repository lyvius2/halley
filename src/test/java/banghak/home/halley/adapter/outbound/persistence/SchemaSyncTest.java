package banghak.home.halley.adapter.outbound.persistence;

import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 스키마와 운영 DDL이 어긋나지 않았는지 본다.
 *
 * <p><b>어긋나도 아무 테스트도 실패하지 않는 것</b>이 문제였습니다. 테스트는 H2(`schema.sql`)만
 * 보고 돌므로, 운영 DDL에 테이블이나 컬럼을 빠뜨려도 전부 초록불입니다 —
 * 배포하고 나서야 {@code relation "system_config" does not exist} 로 알게 됩니다.
 * 실제로 한 번 겪은 일입니다.
 *
 * <p>그래서 <b>실제로 뜬 H2 스키마</b>를 기준으로 두 DDL 파일을 대조합니다.
 * SQL을 파싱하는 쪽은 근사치지만, 기준이 되는 왼쪽은 근사치가 아닙니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("스키마 동기화 — 로컬과 운영 DDL")
class SchemaSyncTest {

    /** 로컬에만 두는 것. 있으면 여기에 이유와 함께 적는다. */
    private static final Set<String> LOCAL_ONLY_TABLES = Set.of();

    @Autowired
    private DSLContext dsl;

    @Test
    @DisplayName("H2에 있는 테이블·컬럼이 DDL.sql에 모두 있다")
    void productionDdlCoversLocalSchema() {
        assertCovers(parse(Path.of("docs/DDL.sql")), "docs/DDL.sql");
    }

    @Test
    @DisplayName("H2에 있는 테이블·컬럼이 DDL-repair.sql에도 모두 있다")
    void repairDdlCoversLocalSchema() {
        assertCovers(parse(Path.of("docs/DDL-repair.sql")), "docs/DDL-repair.sql");
    }

    @Test
    @DisplayName("DDL.sql에만 있는 테이블은 없다 — 테스트가 한 번도 안 만져 본 테이블이 된다")
    void noTableOnlyInProduction() {
        final Set<String> extra = new TreeSet<>(parse(Path.of("docs/DDL.sql")).keySet());
        extra.removeAll(localTables().keySet());
        assertThat(extra)
                .as("docs/DDL.sql 에만 있는 테이블 — schema.sql 에도 넣어야 테스트가 만진다")
                .isEmpty();
    }

    private void assertCovers(Map<String, Set<String>> ddl, String label) {
        final Map<String, Set<String>> local = localTables();
        final List<String> gaps = new ArrayList<>();
        local.forEach((table, columns) -> {
            if (LOCAL_ONLY_TABLES.contains(table)) {
                return;
            }
            final Set<String> theirs = ddl.get(table);
            if (theirs == null) {
                gaps.add("테이블 없음: " + table);
                return;
            }
            new TreeSet<>(columns).stream()
                    .filter(c -> !theirs.contains(c))
                    .forEach(c -> gaps.add("컬럼 없음: " + table + "." + c));
        });
        assertThat(gaps).as(label + " 에 빠진 것").isEmpty();
    }

    /** 실제로 뜬 H2 스키마. 여기가 기준이다. */
    private Map<String, Set<String>> localTables() {
        final Map<String, Set<String>> tables = new HashMap<>();
        dsl.meta().getTables().stream()
                .filter(t -> t.getSchema() != null
                        && "PUBLIC".equalsIgnoreCase(t.getSchema().getName()))
                .forEach(t -> {
                    final Set<String> columns = new HashSet<>();
                    t.fieldStream().forEach(f -> columns.add(f.getName().toLowerCase()));
                    tables.put(t.getName().toLowerCase(), columns);
                });
        return tables;
    }

    // CREATE TABLE 본문과 ALTER TABLE ADD COLUMN 을 모은다. 주석은 걷어낸다.
    private static final Pattern COMMENT = Pattern.compile("--[^\\n]*");
    private static final Pattern CREATE =
            Pattern.compile("CREATE TABLE (?:IF NOT EXISTS )?(\\w+)\\s*\\((.*?)\\n\\);",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD_COLUMN =
            Pattern.compile("ALTER TABLE (\\w+)\\s+ADD COLUMN (?:IF NOT EXISTS )?(\\w+)",
                    Pattern.CASE_INSENSITIVE);
    private static final Set<String> NOT_A_COLUMN =
            Set.of("PRIMARY", "UNIQUE", "FOREIGN", "CONSTRAINT", "CHECK");

    private Map<String, Set<String>> parse(Path path) {
        final String sql;
        try {
            sql = COMMENT.matcher(Files.readString(path)).replaceAll("");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final Map<String, Set<String>> tables = new HashMap<>();
        final Matcher create = CREATE.matcher(sql);
        while (create.find()) {
            tables.computeIfAbsent(create.group(1).toLowerCase(), k -> new HashSet<>())
                    .addAll(columnsOf(create.group(2)));
        }
        final Matcher added = ADD_COLUMN.matcher(sql);
        while (added.find()) {
            tables.computeIfAbsent(added.group(1).toLowerCase(), k -> new HashSet<>())
                    .add(added.group(2).toLowerCase());
        }
        return tables;
    }

    /** 괄호 안의 쉼표는 자르지 않는다 — {@code NUMERIC(10, 2)} 가 컬럼 두 개로 보인다. */
    private Set<String> columnsOf(String body) {
        final Set<String> columns = new HashSet<>();
        final StringBuilder current = new StringBuilder();
        int depth = 0;
        for (final char c : (body + ",").toCharArray()) {
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                final String line = current.toString().trim();
                current.setLength(0);
                if (line.isEmpty()) continue;
                final String first = line.split("\\s+")[0];
                if (!NOT_A_COLUMN.contains(first.toUpperCase())) {
                    columns.add(first.toLowerCase());
                }
            } else {
                current.append(c);
            }
        }
        return columns;
    }
}
