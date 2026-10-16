package org.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;

@DisabledInNativeImage
public class HumanCollationTest {
    private Connection conn;
    private Statement stat;

    @BeforeEach
    public void connect() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        stat = conn.createStatement();
    }

    @AfterEach
    public void close() throws SQLException {
        stat.close();
        conn.close();
    }

    private List<String> column(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = stat.executeQuery(sql)) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    private static List<String> expectedOrder() throws Exception {
        List<String> out = new ArrayList<>();
        try (BufferedReader in =
                new BufferedReader(
                        new InputStreamReader(
                                HumanCollationTest.class.getResourceAsStream(
                                        "human-collation-order.txt"),
                                StandardCharsets.UTF_8))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) out.add(line);
        }
        return out;
    }

    @Test
    public void registeredOnEveryConnection() throws Exception {
        assertThat(column("select human_version()").get(0))
                .matches("\\d+(\\.\\d+)*/icu-\\d+(\\.\\d+)*");
    }

    @Test
    public void naturalCaseInsensitiveOrder() throws Exception {
        stat.executeUpdate("create table t (name text collate HUMAN)");
        stat.executeUpdate(
                "insert into t values ('Auto11'),('Zebra'),('Auto2'),('čevapi'),('Apple'),('Auto02'),('Cesta'),('apple')");
        assertThat(column("select name from t order by name"))
                .containsExactly(
                        "apple", "Apple", "Auto02", "Auto2", "Auto11", "Cesta", "čevapi", "Zebra");
        assertThat(column("select min(name) || '|' || max(name) from t"))
                .containsExactly("apple|Zebra");
        assertThat(column("select count(*) from t where name < 'Auto10'")).containsExactly("4");
    }

    @Test
    public void equalityStaysExact() throws Exception {
        stat.executeUpdate("create table u (name text collate HUMAN unique)");
        stat.executeUpdate("insert into u values ('Auto2'),('Auto02'),('apple'),('Apple')");
        assertThat(column("select count(*) from u")).containsExactly("4");
        assertThat(
                        column(
                                "select ('Auto2' = 'Auto02' collate HUMAN) || ('apple' = 'Apple' collate HUMAN)"))
                .containsExactly("00");
        assertThatThrownBy(() -> stat.executeUpdate("insert into u values ('Auto2')"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    public void matchesPostgresIcuOrder() throws Exception {
        List<String> expected = expectedOrder();
        List<String> input = new ArrayList<>(expected);
        Collections.reverse(input);
        stat.executeUpdate("create table c (name text collate HUMAN)");
        try (PreparedStatement ps = conn.prepareStatement("insert into c values (?)")) {
            for (String s : input) {
                ps.setString(1, s);
                ps.executeUpdate();
            }
        }
        assertThat(column("select name from c order by name")).containsExactlyElementsOf(expected);
        stat.executeUpdate("create index c_name on c (name)");
        assertThat(column("select name from c indexed by c_name order by name"))
                .containsExactlyElementsOf(expected);
    }

    @Test
    public void builtinFunctionsUnchanged() throws Exception {
        assertThat(column("select lower('ČŠŽ') || '|' || upper('čšž') || '|' || ('Č' like 'č')"))
                .containsExactly("ČŠŽ|čšž|0");
    }
}
