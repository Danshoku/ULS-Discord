package server;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int PORT = 1234;
    private static Set<ClientHandler> clientHandlers = new HashSet<>();
    private static Set<String> connectedUsers = new HashSet<>();
    private static DatabaseManager dbManager;

    public static void main(String[] args) {
        System.out.println(">>> Serveur Discord (Fixé) démarré...");
        dbManager = new DatabaseManager();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void broadcastSystem(String msg) {
        for (ClientHandler client : clientHandlers) client.sendMessage(msg);
    }

    public static void broadcastMessage(String sender, String content, String context) {
        dbManager.saveMessage(sender, content, context);
        // CORRECTION : Utilisation de /// pour séparer le protocole réseau
        String proto = "MSG:" + context + "///" + sender + "///" + content;
        for (ClientHandler client : clientHandlers) client.sendMessage(proto);
    }

    public static void broadcastUserList() {
        String list = "USERLIST:" + String.join(",", connectedUsers);
        for (ClientHandler client : clientHandlers) client.sendMessage(list);
    }

    public static void sendFriendList(ClientHandler client) {
        List<String> friends = dbManager.getFriendsList(client.username);
        client.sendMessage("FRIENDLIST:" + String.join(",", friends));
    }

    public static String getMPContext(String u1, String u2) {
        String[] users = {u1, u2};
        Arrays.sort(users);
        return "MP:" + users[0] + ":" + users[1];
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket s) { this.socket = s; }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String u = in.readLine();
                String p = in.readLine();

                if (dbManager.checkUser(u, p) != 0) { out.println("FAIL:Identifiants incorrects"); return; }

                this.username = u;
                out.println("SUCCESS");

                synchronized (clientHandlers) { clientHandlers.add(this); }
                synchronized (connectedUsers) { connectedUsers.add(this.username); }
                Server.broadcastUserList();

                // Envois initiaux
                for (String h : dbManager.getRelevantHistory(username)) out.println(h);
                Server.sendFriendList(this);

                List<String> servers = dbManager.getAllServers();
                for (String srv : servers) {
                    out.println("NEW_SERVER:" + srv);
                    List<String> chans = dbManager.getChannels(srv);
                    for (String chan : chans) out.println("NEW_CHANNEL:" + srv + "|" + chan);
                }

                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.startsWith("/create_server ")) {
                        String name = msg.substring(15).trim();
                        if (dbManager.createServer(name, username)) {
                            Server.broadcastSystem("NEW_SERVER:" + name);
                            Server.broadcastSystem("NEW_CHANNEL:" + name + "|#general");
                        }
                    }
                    else if (msg.startsWith("/create_channel ")) {
                        String[] parts = msg.split(" ", 3);
                        if (parts.length == 3 && dbManager.createChannel(parts[1], parts[2])) {
                            Server.broadcastSystem("NEW_CHANNEL:" + parts[1] + "|" + parts[2]);
                        }
                    }
                    // CORRECTION : Détection du séparateur ///
                    else if (msg.contains("///")) {
                        // Le client envoie : CONTEXTE///MESSAGE
                        int idx = msg.indexOf("///");
                        String ctx = msg.substring(0, idx);
                        String content = msg.substring(idx + 3); // +3 pour sauter les ///

                        if (ctx.startsWith("MP:")) {
                            String target = ctx.substring(3);
                            String normalized = Server.getMPContext(username, target);
                            Server.broadcastMessage(username, content, normalized);
                        } else {
                            // Ici ctx ressemble à "Serveur|#salon"
                            Server.broadcastMessage(username, content, ctx);
                        }
                    }
                    else if (msg.startsWith("/friend add ")) {
                        String target = msg.substring(12).trim();
                        if (!target.equalsIgnoreCase(username)) {
                            if (dbManager.sendFriendRequest(username, target)) {
                                for (ClientHandler c : clientHandlers) {
                                    if (c.username.equalsIgnoreCase(target)) c.sendMessage("FRIEND_REQ:" + username);
                                }
                            }
                        }
                    }
                    else if (msg.startsWith("/friend accept ")) {
                        String requester = msg.substring(15).trim();
                        dbManager.sendFriendRequest(requester, username);
                        Server.sendFriendList(this);
                        for (ClientHandler c : clientHandlers) {
                            if (c.username.equalsIgnoreCase(requester)) Server.sendFriendList(c);
                        }
                    }
                }
            } catch (IOException e) {
            } finally {
                synchronized (clientHandlers) { clientHandlers.remove(this); }
                synchronized (connectedUsers) { connectedUsers.remove(this.username); }
                Server.broadcastUserList();
                try { socket.close(); } catch (IOException e) {}
            }
        }
        public void sendMessage(String m) { out.println(m); }
    }
}