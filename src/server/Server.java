package server;

import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Server {
    private static final int PORT = 1234;
    private static Set<ClientHandler> clientHandlers = new HashSet<>();
    private static Set<String> connectedUsers = new HashSet<>();
    private static DatabaseManager dbManager;

    public static void main(String[] args) {
        dbManager = new DatabaseManager();

        System.out.println(">>> Serveur Discord (Ultimate) démarré...");
        System.out.println("------------------------------------------------");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        System.out.println("✅ IP À UTILISER SUR LE CLIENT : " + addr.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) { e.printStackTrace(); }
        System.out.println("Port TCP (Chat)  : " + PORT);
        System.out.println("Port UDP (Vocal) : 1235");
        System.out.println("------------------------------------------------");

        new Thread(new VoiceServer()).start();

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
        // Fusion : Ajout de l'heure
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String proto = "MSG:" + context + "///" + sender + "///" + content + "///" + time;
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

    // Fusion : Envoi de la liste des membres pour le statut hors-ligne
    public static void sendServerMembers(ClientHandler client, String serverName) {
        List<String> members = dbManager.getServerMembers(serverName);
        client.sendMessage("SERVER_MEMBERS:" + serverName + "|" + String.join(",", members));
    }

    public static String getMPContext(String u1, String u2) {
        String[] users = { u1, u2 }; Arrays.sort(users); return "MP:" + users[0] + ":" + users[1];
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

                for (String h : dbManager.getRelevantHistory(username)) out.println(h);
                Server.sendFriendList(this);

                List<String> myServers = dbManager.getUserServers(username);
                for (String srv : myServers) {
                    out.println("NEW_SERVER:" + srv);
                    Server.sendServerMembers(this, srv);
                    List<String> chans = dbManager.getChannels(srv);
                    for (String chan : chans) out.println("NEW_CHANNEL:" + srv + "|" + chan);
                }

                String msg;
                while ((msg = in.readLine()) != null) {
                    System.out.println("[DEBUG] " + username + ": " + msg);

                    if (msg.startsWith("/create_server ")) {
                        String name = msg.substring(15).trim();
                        if (dbManager.createServer(name, username)) {
                            this.sendMessage("NEW_SERVER:" + name);
                            Server.sendServerMembers(this, name);
                            this.sendMessage("NEW_CHANNEL:" + name + "|#general");
                        } else {
                            this.sendMessage("MSG:HOME///Système///Erreur: Nom déjà pris.///00:00");
                        }
                    }
                    else if (msg.startsWith("/create_invite ")) {
                        String srv = msg.substring(15).trim();
                        String code = dbManager.createInvite(srv);
                        if (code != null) this.sendMessage("INVITE_CODE:" + code);
                        else this.sendMessage("MSG:HOME///Système///Erreur invitation.///00:00");
                    }
                    else if (msg.startsWith("/join ")) { // Via Code
                        String code = msg.substring(6).trim();
                        String realName = dbManager.getServerFromInvite(code);
                        if (realName != null) {
                            String joined = dbManager.joinServer(realName, username);
                            if (joined != null) {
                                this.sendMessage("NEW_SERVER:" + joined);
                                Server.sendServerMembers(this, joined);
                                List<String> chans = dbManager.getChannels(joined);
                                for (String chan : chans) this.sendMessage("NEW_CHANNEL:" + joined + "|" + chan);
                                this.sendMessage("MSG:HOME///Système///Bienvenue sur : " + joined + "///00:00");
                            }
                        } else {
                            this.sendMessage("MSG:HOME///Système///Invitation invalide.///00:00");
                        }
                    }
                    else if (msg.startsWith("/join_server ")) { // Via Nom (Backup)
                        String inputName = msg.substring(13).trim();
                        String realName = dbManager.joinServer(inputName, username);
                        if (realName != null) {
                            this.sendMessage("NEW_SERVER:" + realName);
                            Server.sendServerMembers(this, realName);
                            List<String> chans = dbManager.getChannels(realName);
                            for (String chan : chans) this.sendMessage("NEW_CHANNEL:" + realName + "|" + chan);
                            this.sendMessage("MSG:HOME///Système///Bienvenue sur : " + realName + "///00:00");
                        } else {
                            this.sendMessage("MSG:HOME///Système///Serveur introuvable.///00:00");
                        }
                    }
                    else if (msg.startsWith("/leave ")) {
                        String srv = msg.substring(7).trim();
                        if (dbManager.leaveServer(username, srv)) {
                            this.sendMessage("LEFT_SERVER:" + srv);
                            this.sendMessage("MSG:HOME///Système///Vous avez quitté " + srv + "///00:00");
                        }
                    }
                    else if (msg.startsWith("/create_channel ")) {
                        String[] parts = msg.split(" ", 3);
                        if (parts.length == 3 && dbManager.createChannel(parts[1], parts[2])) {
                            Server.broadcastSystem("NEW_CHANNEL:" + parts[1] + "|" + parts[2]);
                        }
                    }
                    else if (msg.contains("///")) {
                        int idx = msg.indexOf("///");
                        String ctx = msg.substring(0, idx);
                        String content = msg.substring(idx + 3);
                        if (ctx.startsWith("MP:")) {
                            String target = ctx.substring(3);
                            Server.broadcastMessage(username, content, Server.getMPContext(username, target));
                        } else {
                            Server.broadcastMessage(username, content, ctx);
                        }
                    }
                    else if (msg.startsWith("/friend add ")) {
                        String target = msg.substring(12).trim();
                        if (!target.equalsIgnoreCase(username) && dbManager.sendFriendRequest(username, target)) {
                            for (ClientHandler c : clientHandlers) if (c.username.equalsIgnoreCase(target)) c.sendMessage("FRIEND_REQ:" + username);
                        }
                    }
                    else if (msg.startsWith("/friend accept ")) {
                        String req = msg.substring(15).trim();
                        dbManager.sendFriendRequest(req, username);
                        Server.sendFriendList(this);
                        for (ClientHandler c : clientHandlers) if (c.username.equalsIgnoreCase(req)) Server.sendFriendList(c);
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

    private static class VoiceServer implements Runnable {
        private static final int VOICE_PORT = 1235;
        private Map<String, Set<String>> voiceChannels = new HashMap<>();

        @Override
        public void run() {
            System.out.println(">>> Serveur Vocal (UDP) prêt sur le port " + VOICE_PORT);
            try (DatagramSocket socket = new DatagramSocket(VOICE_PORT)) {
                byte[] buffer = new byte[4096];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    byte[] data = packet.getData();
                    int len = packet.getLength();

                    int separatorIndex = -1;
                    for (int i = 0; i < len; i++) { if (data[i] == 0) { separatorIndex = i; break; } }

                    if (separatorIndex > 0) {
                        String header = new String(data, 0, separatorIndex);
                        InetAddress clientIP = packet.getAddress();
                        int clientPort = packet.getPort();
                        String clientKey = clientIP.getHostAddress() + ":" + clientPort;

                        voiceChannels.computeIfAbsent(header, k -> new HashSet<>()).add(clientKey);
                        Set<String> participants = voiceChannels.get(header);
                        for (String participant : participants) {
                            if (!participant.equals(clientKey)) {
                                try {
                                    int lastColon = participant.lastIndexOf(":");
                                    String ipStr = participant.substring(0, lastColon);
                                    int port = Integer.parseInt(participant.substring(lastColon + 1));
                                    InetAddress targetIP = InetAddress.getByName(ipStr);
                                    DatagramPacket forward = new DatagramPacket(data, len, targetIP, port);
                                    socket.send(forward);
                                } catch(Exception e) { System.err.println("Erreur relais vocal"); }
                            }
                        }
                    }
                }
            } catch (IOException e) { e.printStackTrace(); }
        }
    }
}