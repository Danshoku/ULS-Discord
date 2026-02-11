package server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private Connection connection;

    public DatabaseManager() {
        try {
            String path = System.getProperty("user.dir") + java.io.File.separator + "discord.db";
            String url = "jdbc:sqlite:" + path;
            connection = DriverManager.getConnection(url);
            createTables();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- MODIFICATION ICI : Ajout des tables roles et user_roles ---
    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, password TEXT NOT NULL)");
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS friends (id INTEGER PRIMARY KEY AUTOINCREMENT, requester TEXT, target TEXT, status TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS servers (name TEXT PRIMARY KEY, owner TEXT)");
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS server_members (id INTEGER PRIMARY KEY AUTOINCREMENT, server_name TEXT, username TEXT)");
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS channels (id INTEGER PRIMARY KEY AUTOINCREMENT, server_name TEXT, channel_name TEXT)");
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT, content TEXT, context TEXT, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");
            stmt.execute("CREATE TABLE IF NOT EXISTS invites (code TEXT PRIMARY KEY, server_name TEXT)");

            // NOUVELLES TABLES POUR LES PERMISSIONS
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS roles (id INTEGER PRIMARY KEY AUTOINCREMENT, server_name TEXT, role_name TEXT, permissions INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS user_roles (server_name TEXT, username TEXT, role_id INTEGER)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ... (GARDE TOUTES TES MÉTHODES EXISTANTES : getServerMembers, joinServer,
    // etc.) ...
    // ... (Ne touche pas à tes méthodes existantes, ajoute juste la suite à la fin)
    // ...

    public List<String> getServerMembers(String serverName) {
        List<String> members = new ArrayList<>();
        try (PreparedStatement p = connection
                .prepareStatement("SELECT username FROM server_members WHERE server_name = ?")) {
            p.setString(1, serverName);
            ResultSet rs = p.executeQuery();
            while (rs.next())
                members.add(rs.getString("username"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return members;
    }

    public String joinServer(String inputName, String username) {
        String realName = null;
        try (PreparedStatement check = connection
                .prepareStatement("SELECT name FROM servers WHERE LOWER(name) = LOWER(?)")) {
            check.setString(1, inputName);
            ResultSet rs = check.executeQuery();
            if (rs.next())
                realName = rs.getString("name");
            else
                return null;
        } catch (SQLException e) {
            return null;
        }

        try (PreparedStatement check = connection
                .prepareStatement("SELECT 1 FROM server_members WHERE server_name = ? AND username = ?")) {
            check.setString(1, realName);
            check.setString(2, username);
            if (check.executeQuery().next())
                return realName;
        } catch (SQLException e) {
            return null;
        }

        try (PreparedStatement pstmt = connection
                .prepareStatement("INSERT INTO server_members(server_name, username) VALUES(?, ?)")) {
            pstmt.setString(1, realName);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
            return realName;
        } catch (SQLException e) {
            return null;
        }
    }

    public boolean createServer(String name, String owner) {
        try (PreparedStatement check = connection
                .prepareStatement("SELECT name FROM servers WHERE LOWER(name) = LOWER(?)")) {
            check.setString(1, name);
            if (check.executeQuery().next())
                return false;
        } catch (SQLException e) {
            return false;
        }

        try (PreparedStatement pstmt = connection.prepareStatement("INSERT INTO servers(name, owner) VALUES(?, ?)")) {
            pstmt.setString(1, name);
            pstmt.setString(2, owner);
            pstmt.executeUpdate();
            createChannel(name, "#general");
            joinServer(name, owner);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public String createInvite(String serverName) {
        String code = generateRandomCode(6);
        try (PreparedStatement u = connection.prepareStatement("INSERT INTO invites(code, server_name) VALUES(?, ?)")) {
            u.setString(1, code);
            u.setString(2, serverName);
            u.executeUpdate();
            return code;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getServerFromInvite(String code) {
        try (PreparedStatement p = connection.prepareStatement("SELECT server_name FROM invites WHERE code=?")) {
            p.setString(1, code);
            ResultSet rs = p.executeQuery();
            return rs.next() ? rs.getString("server_name") : null;
        } catch (SQLException e) {
            return null;
        }
    }

    public boolean leaveServer(String username, String serverName) {
        try (PreparedStatement p = connection
                .prepareStatement("DELETE FROM server_members WHERE username=? AND server_name=?")) {
            p.setString(1, username);
            p.setString(2, serverName);
            return p.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    private String generateRandomCode(int len) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < len; i++)
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    public List<String> getUserServers(String username) {
        List<String> l = new ArrayList<>();
        try (PreparedStatement p = connection
                .prepareStatement("SELECT server_name FROM server_members WHERE username = ?")) {
            p.setString(1, username);
            ResultSet rs = p.executeQuery();
            while (rs.next())
                l.add(rs.getString("server_name"));
        } catch (SQLException e) {
        }
        return l;
    }

    public void saveMessage(String sender, String content, String context) {
        try (PreparedStatement pstmt = connection
                .prepareStatement("INSERT INTO messages(sender, content, context) VALUES(?, ?, ?)")) {
            pstmt.setString(1, sender);
            pstmt.setString(2, content);
            pstmt.setString(3, context);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getRelevantHistory(String username) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT sender, content, context, strftime('%H:%M', timestamp) as timeStr FROM messages WHERE context NOT LIKE 'MP:%' OR context LIKE ? OR context LIKE ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, "MP:" + username + ":%");
            pstmt.setString(2, "MP:%:" + username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                history.add("HISTORY:" + rs.getString("context") + "///" + rs.getString("sender") + "///"
                        + rs.getString("content") + "///" + rs.getString("timeStr"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }

    public boolean createChannel(String srv, String chan) {
        try (PreparedStatement pstmt = connection
                .prepareStatement("INSERT INTO channels(server_name, channel_name) VALUES(?, ?)")) {
            pstmt.setString(1, srv);
            pstmt.setString(2, chan);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean renameChannel(String srv, String oldName, String newName) {
        try (PreparedStatement pstmt = connection
                .prepareStatement("UPDATE channels SET channel_name = ? WHERE server_name = ? AND channel_name = ?")) {
            pstmt.setString(1, newName);
            pstmt.setString(2, srv);
            pstmt.setString(3, oldName);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public List<String> getChannels(String srv) {
        List<String> l = new ArrayList<>();
        try (PreparedStatement p = connection
                .prepareStatement("SELECT channel_name FROM channels WHERE server_name = ?")) {
            p.setString(1, srv);
            ResultSet rs = p.executeQuery();
            while (rs.next())
                l.add(rs.getString("channel_name"));
        } catch (SQLException e) {
        }
        return l;
    }

    public int checkUser(String u, String p) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT password FROM users WHERE username = ?")) {
            ps.setString(1, u);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return rs.getString("password").equals(p) ? 0 : 1;
            else {
                try (PreparedStatement cr = connection.prepareStatement("INSERT INTO users VALUES(?, ?)")) {
                    cr.setString(1, u);
                    cr.setString(2, p);
                    cr.executeUpdate();
                    return 0;
                }
            }
        } catch (SQLException e) {
            return 2;
        }
    }

    public boolean sendFriendRequest(String f, String t) {
        try (PreparedStatement p = connection
                .prepareStatement("INSERT INTO friends(requester, target, status) VALUES(?,?,'ACCEPTED')")) {
            p.setString(1, f);
            p.setString(2, t);
            p.executeUpdate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> getFriendsList(String u) {
        List<String> l = new ArrayList<>();
        try (PreparedStatement p = connection
                .prepareStatement("SELECT requester, target FROM friends WHERE requester=? OR target=?")) {
            p.setString(1, u);
            p.setString(2, u);
            ResultSet rs = p.executeQuery();
            while (rs.next())
                l.add(rs.getString(1).equals(u) ? rs.getString(2) : rs.getString(1));
        } catch (Exception e) {
        }
        return l;
    }

    public List<String> getAllServers() {
        List<String> l = new ArrayList<>();
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT name FROM servers")) {
            while (rs.next())
                l.add(rs.getString("name"));
        } catch (SQLException e) {
        }
        return l;
    }

    // --- AJOUT : SYSTÈME DE RÔLES ---

    // Récupère les permissions totales d'un utilisateur (Owner = 9999, sinon somme
    // des rôles)
    public int getUserPermissions(String serverName, String username) {
        try (PreparedStatement checkOwner = connection.prepareStatement("SELECT owner FROM servers WHERE name = ?")) {
            checkOwner.setString(1, serverName);
            ResultSet rs = checkOwner.executeQuery();
            if (rs.next() && username.equals(rs.getString("owner")))
                return 9999;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        int totalPerms = 0;
        String sql = "SELECT r.permissions FROM user_roles ur JOIN roles r ON ur.role_id = r.id WHERE ur.server_name = ? AND ur.username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, serverName);
            ps.setString(2, username);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                totalPerms |= rs.getInt("permissions");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return totalPerms;
    }

    // Créer un rôle
    public boolean createRole(String serverName, String roleName, int permissions) {
        try (PreparedStatement ps = connection
                .prepareStatement("INSERT INTO roles(server_name, role_name, permissions) VALUES(?, ?, ?)")) {
            ps.setString(1, serverName);
            ps.setString(2, roleName);
            ps.setInt(3, permissions);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    // Assigner un rôle
    public boolean assignRole(String serverName, String targetUser, String roleName) {
        int roleId = -1;
        try (PreparedStatement ps = connection
                .prepareStatement("SELECT id FROM roles WHERE server_name=? AND role_name=?")) {
            ps.setString(1, serverName);
            ps.setString(2, roleName);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                roleId = rs.getInt("id");
            else
                return false;
        } catch (SQLException e) {
            return false;
        }

        try (PreparedStatement ps = connection
                .prepareStatement("INSERT INTO user_roles(server_name, username, role_id) VALUES(?, ?, ?)")) {
            ps.setString(1, serverName);
            ps.setString(2, targetUser);
            ps.setInt(3, roleId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}