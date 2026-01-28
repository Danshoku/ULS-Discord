package server;

import java.sql.*;

public class ReproduceHistory {
    public static void main(String[] args) {
        System.out.println("Starting reproduction test...");

        try {
            Class.forName("org.sqlite.JDBC");
            System.out.println("SQLite driver loaded.");
        } catch (ClassNotFoundException e) {
            System.out.println("SQLite driver NOT found!");
            return;
        }

        // Initialize DatabaseManager to ensure schema is up to date (Migration)
        // Since we are in the same package 'server', we can access it if it is public.
        // Actually DatabaseManager is public.
        // But previously ReproduceHistory was in default package.
        new DatabaseManager();

        String url = "jdbc:sqlite:discord.db";
        try (Connection conn = DriverManager.getConnection(url)) {
            System.out.println("Connected to DB.");

            // 1. Verify table exists
            // 2. Insert test message
            String sender = "Alice";

            String content = "Hello Bob " + System.currentTimeMillis();
            String context = "MP:Alice:Bob"; // Normalized

            String insertSql = "INSERT INTO messages(sender, content, context) VALUES(?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, sender);
                pstmt.setString(2, content);
                pstmt.setString(3, context);
                pstmt.executeUpdate();
                System.out.println("Inserted message: " + content);
            }

            // 3. Query history for Alice
            System.out.println("Querying history for Alice...");
            queryHistory(conn, "Alice");

            // 4. Query history for Bob
            System.out.println("Querying history for Bob...");
            queryHistory(conn, "Bob");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void queryHistory(Connection conn, String username) throws SQLException {
        String sql = "SELECT sender, content, context FROM messages " +
                "WHERE context NOT LIKE 'MP:%' " +
                "OR context LIKE ? " +
                "OR context LIKE ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "MP:" + username + ":%");
            pstmt.setString(2, "MP:%:" + username);

            ResultSet rs = pstmt.executeQuery();
            int count = 0;
            while (rs.next()) {
                count++;
                String ctx = rs.getString("context");
                String snd = rs.getString("sender");
                String cnt = rs.getString("content");
                System.out.println("Found: " + ctx + " | " + snd + " | " + cnt);
            }
            System.out.println("Total messages found for " + username + ": " + count);
        }
    }
}