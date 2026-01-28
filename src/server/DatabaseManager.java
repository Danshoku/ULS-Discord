package server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private Connection connection;

    public DatabaseManager() {
        try {
            String url = "jdbc:sqlite:discord.db";
            connection = DriverManager.getConnection(url);
            createTables();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, password TEXT NOT NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS friends (id INTEGER PRIMARY KEY AUTOINCREMENT, requester TEXT, target TEXT, status TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS servers (name TEXT PRIMARY KEY, owner TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS server_members (id INTEGER PRIMARY KEY AUTOINCREMENT, server_name TEXT, username TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS channels (id INTEGER PRIMARY KEY AUTOINCREMENT, server_name TEXT, channel_name TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "sender TEXT, content TEXT, context TEXT, " +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // --- GESTION SERVEURS ---

    public String joinServer(String inputName, String username) {
        String realName = null;
        // Recherche insensible à la casse
        try (PreparedStatement check = connection.prepareStatement("SELECT name FROM servers WHERE LOWER(name) = LOWER(?)")) {
            check.setString(1, inputName);
            ResultSet rs = check.executeQuery();
            if (rs.next()) realName = rs.getString("name");
            else return null;
        } catch (SQLException e) { return null; }

        // Vérification membre
        try (PreparedStatement check = connection.prepareStatement("SELECT id FROM server_members WHERE server_name = ? AND username = ?")) {
            check.setString(1, realName); check.setString(2, username);
            if (check.executeQuery().next()) return realName;
        } catch (SQLException e) { return null; }

        // Ajout membre
        try (PreparedStatement pstmt = connection.prepareStatement("INSERT INTO server_members(server_name, username) VALUES(?, ?)")) {
            pstmt.setString(1, realName); pstmt.setString(2, username);
            pstmt.executeUpdate();
            return realName;
        } catch (SQLException e) { return null; }
    }

    public boolean createServer(String name, String owner) {
        try (PreparedStatement check = connection.prepareStatement("SELECT name FROM servers WHERE LOWER(name) = LOWER(?)")) {
            check.setString(1, name);
            if (check.executeQuery().next()) return false;
        } catch (SQLException e) { return false; }

        try (PreparedStatement pstmt = connection.prepareStatement("INSERT INTO servers(name, owner) VALUES(?, ?)")) {
            pstmt.setString(1, name); pstmt.setString(2, owner); pstmt.executeUpdate();
            createChannel(name, "#general");
            joinServer(name, owner);
            return true;
        } catch (SQLException e) { return false; }
    }

    public List<String> getUserServers(String username) {
        List<String> l = new ArrayList<>();
        try (PreparedStatement p = connection.prepareStatement("SELECT server_name FROM server_members WHERE username = ?")) {
            p.setString(1, username); ResultSet rs = p.executeQuery();
            while(rs.next()) l.add(rs.getString("server_name"));
        } catch (SQLException e) {} return l;
    }

    // --- GESTION MESSAGES & CANAUX ---

    public void saveMessage(String sender, String content, String context) {
        try (PreparedStatement pstmt = connection.prepareStatement("INSERT INTO messages(sender, content, context) VALUES(?, ?, ?)")) {
            pstmt.setString(1, sender); pstmt.setString(2, content); pstmt.setString(3, context); pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public List<String> getRelevantHistory(String username) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT sender, content, context FROM messages WHERE context NOT LIKE 'MP:%' OR context LIKE ? OR context LIKE ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, "MP:" + username + ":%"); pstmt.setString(2, "MP:%:" + username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) history.add("HISTORY:" + rs.getString("context") + "///" + rs.getString("sender") + "///" + rs.getString("content"));
        } catch (SQLException e) { e.printStackTrace(); } return history;
    }

    public boolean createChannel(String srv, String chan) {
        try (PreparedStatement pstmt = connection.prepareStatement("INSERT INTO channels(server_name, channel_name) VALUES(?, ?)")) {
            pstmt.setString(1, srv); pstmt.setString(2, chan); pstmt.executeUpdate(); return true;
        } catch (SQLException e) { return false; }
    }

    public List<String> getChannels(String srv) {
        List<String> l = new ArrayList<>();
        try (PreparedStatement p = connection.prepareStatement("SELECT channel_name FROM channels WHERE server_name = ?")) {
            p.setString(1, srv); ResultSet rs = p.executeQuery();
            while(rs.next()) l.add(rs.getString("channel_name"));
        } catch (SQLException e) {} return l;
    }

    // --- UTILISATEURS & AMIS ---

    public int checkUser(String u, String p) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT password FROM users WHERE username = ?")) {
            ps.setString(1, u); ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("password").equals(p) ? 0 : 1;
            else { try(PreparedStatement cr = connection.prepareStatement("INSERT INTO users VALUES(?, ?)")) {
                cr.setString(1, u); cr.setString(2, p); cr.executeUpdate(); return 0; } }
        } catch (SQLException e) { return 2; }
    }

    public boolean sendFriendRequest(String f, String t) {
        try(PreparedStatement p = connection.prepareStatement("INSERT INTO friends(requester, target, status) VALUES(?,?,'ACCEPTED')")){
            p.setString(1, f); p.setString(2, t); p.executeUpdate(); return true;
        } catch(Exception e){return false;}
    }

    public List<String> getFriendsList(String u) {
        List<String> l = new ArrayList<>();
        try(PreparedStatement p = connection.prepareStatement("SELECT requester, target FROM friends WHERE requester=? OR target=?")){
            p.setString(1, u); p.setString(2, u); ResultSet rs = p.executeQuery();
            while(rs.next()) l.add(rs.getString(1).equals(u)?rs.getString(2):rs.getString(1));
        } catch(Exception e){} return l;
    }

    // Gardé pour compatibilité mais non utilisé par le client actuel
    public List<String> getAllServers() {
        List<String> l = new ArrayList<>();
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT name FROM servers")) {
            while(rs.next()) l.add(rs.getString("name"));
        } catch (SQLException e) {} return l;
    }
}