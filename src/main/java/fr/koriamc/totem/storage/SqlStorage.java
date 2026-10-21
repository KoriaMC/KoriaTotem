package fr.koriamc.totem.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Stockage SQL (SQLite, ou MySQL / MariaDB avec le même pilote). Chaque entité est une ligne (id, data JSON) ;
 * seules les lignes modifiées depuis la dernière sauvegarde sont écrites.
 * Les pilotes JDBC sont fournis par Spigot 1.8.8.
 */
public final class SqlStorage implements Storage {

    private static final String[] TABLES = {"players", "factions", "meta"};

    private final boolean mysql;
    private final String url;
    private final String user;
    private final String password;
    private final String prefix;
    private Connection connection;

    @SuppressWarnings("unchecked")
    private final Map<String, String>[] lastSaved = new Map[]{
            new HashMap<String, String>(), new HashMap<String, String>(), new HashMap<String, String>()};

    private SqlStorage(boolean mysql, String url, String user, String password, String prefix) {
        this.mysql = mysql;
        this.url = url;
        this.user = user;
        this.password = password;
        this.prefix = prefix == null ? "" : prefix;
    }

    public static SqlStorage sqlite(File file) {
        return new SqlStorage(false, "jdbc:sqlite:" + file.getAbsolutePath(), null, null, "");
    }

    public static SqlStorage mysql(String host, int port, String database, String user, String password,
                                   String prefix, String parameters) {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + (parameters == null ? "" : parameters);
        return new SqlStorage(true, url, user, password, prefix);
    }

    @Override
    public String getName() {
        return mysql ? "MySQL/MariaDB" : "SQLite";
    }

    @Override
    public void init() throws Exception {
        Class.forName(mysql ? "com.mysql.jdbc.Driver" : "org.sqlite.JDBC");
        Connection conn = connection();
        Statement st = conn.createStatement();
        try {
            for (String table : TABLES) {
                if (mysql) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + table + "` ("
                            + "`id` VARCHAR(128) NOT NULL PRIMARY KEY, `data` LONGTEXT NOT NULL"
                            + ") DEFAULT CHARSET=utf8");
                } else {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + table
                            + " (id TEXT NOT NULL PRIMARY KEY, data TEXT NOT NULL)");
                }
            }
        } finally {
            st.close();
        }
    }

    private Connection connection() throws SQLException {
        if (connection != null) {
            try {
                if (!connection.isClosed() && (!mysql || connection.isValid(3))) {
                    return connection;
                }
            } catch (SQLException ignored) {
                // reconnexion ci-dessous
            } catch (AbstractMethodError ignored) {
                // pilote trop ancien pour isValid
                return connection;
            }
            try {
                connection.close();
            } catch (SQLException ignored) {
                // déjà fermée
            }
        }
        connection = user == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, password);
        return connection;
    }

    @Override
    public DataSnapshot load() throws Exception {
        DataSnapshot data = new DataSnapshot();
        Map<String, String>[] targets = targets(data);
        Connection conn = connection();
        for (int i = 0; i < TABLES.length; i++) {
            Statement st = conn.createStatement();
            try {
                ResultSet rs = st.executeQuery("SELECT id, data FROM " + table(i));
                while (rs.next()) {
                    targets[i].put(rs.getString(1), rs.getString(2));
                }
                rs.close();
            } finally {
                st.close();
            }
            lastSaved[i].clear();
            lastSaved[i].putAll(targets[i]);
        }
        return data;
    }

    @Override
    public void save(DataSnapshot snapshot) throws Exception {
        Map<String, String>[] current = targets(snapshot);
        Connection conn = connection();
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (int i = 0; i < TABLES.length; i++) {
                writeTable(conn, i, current[i]);
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
        for (int i = 0; i < TABLES.length; i++) {
            lastSaved[i].clear();
            lastSaved[i].putAll(current[i]);
        }
    }

    private void writeTable(Connection conn, int index, Map<String, String> current) throws SQLException {
        Map<String, String> previous = lastSaved[index];
        String upsert = mysql
                ? "INSERT INTO " + table(index) + " (id, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data = VALUES(data)"
                : "INSERT OR REPLACE INTO " + table(index) + " (id, data) VALUES (?, ?)";
        PreparedStatement ps = conn.prepareStatement(upsert);
        try {
            int count = 0;
            for (Map.Entry<String, String> entry : current.entrySet()) {
                if (!entry.getValue().equals(previous.get(entry.getKey()))) {
                    ps.setString(1, entry.getKey());
                    ps.setString(2, entry.getValue());
                    ps.addBatch();
                    count++;
                }
            }
            if (count > 0) {
                ps.executeBatch();
            }
        } finally {
            ps.close();
        }
        Set<String> removed = new HashSet<String>(previous.keySet());
        removed.removeAll(current.keySet());
        if (!removed.isEmpty()) {
            PreparedStatement del = conn.prepareStatement("DELETE FROM " + table(index) + " WHERE id = ?");
            try {
                for (String id : removed) {
                    del.setString(1, id);
                    del.addBatch();
                }
                del.executeBatch();
            } finally {
                del.close();
            }
        }
    }

    private String table(int index) {
        return mysql ? "`" + prefix + TABLES[index] + "`" : prefix + TABLES[index];
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String>[] targets(DataSnapshot data) {
        return new Map[]{data.players, data.factions, data.meta};
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // arrêt du serveur
            }
            connection = null;
        }
    }
}
