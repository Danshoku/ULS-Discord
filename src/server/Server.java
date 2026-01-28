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
        dbManager = new DatabaseManager();

        System.out.println(">>> Serveur Discord (Fixé) démarré...");
        System.out.println("------------------------------------------------");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp())
                    continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        System.out.println("✅ IP À UTILISER SUR LE CLIENT : " + addr.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        System.out.println("Port TCP (Chat)  : " + PORT);
        System.out.println("Port UDP (Vocal) : 1235");
        System.out.println("------------------------------------------------");

        // Démarrage du serveur vocal
        new Thread(new VoiceServer()).start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void broadcastSystem(String msg) {
        for (ClientHandler client : clientHandlers)
            client.sendMessage(msg);
    }

    public static void broadcastMessage(String sender, String content, String context) {
        dbManager.saveMessage(sender, content, context);
        String proto = "MSG:" + context + "///" + sender + "///" + content;
        for (ClientHandler client : clientHandlers)
            client.sendMessage(proto);
    }

    public static void broadcastUserList() {
        String list = "USERLIST:" + String.join(",", connectedUsers);
        for (ClientHandler client : clientHandlers)
            client.sendMessage(list);
    }

    public static void sendFriendList(ClientHandler client) {
        List<String> friends = dbManager.getFriendsList(client.username);
        client.sendMessage("FRIENDLIST:" + String.join(",", friends));
    }

    public static String getMPContext(String u1, String u2) {
        String[] users = { u1, u2 };
        Arrays.sort(users);
        return "MP:" + users[0] + ":" + users[1];
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket s) {
            this.socket = s;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String u = in.readLine();
                String p = in.readLine();

                if (dbManager.checkUser(u, p) != 0) {
                    out.println("FAIL:Identifiants incorrects");
                    return;
                }

                this.username = u;
                out.println("SUCCESS");

                synchronized (clientHandlers) {
                    clientHandlers.add(this);
                }
                synchronized (connectedUsers) {
                    connectedUsers.add(this.username);
                }
                Server.broadcastUserList();

                for (String h : dbManager.getRelevantHistory(username))
                    out.println(h);
                Server.sendFriendList(this);

                List<String> myServers = dbManager.getUserServers(username);
                for (String srv : myServers) {
                    out.println("NEW_SERVER:" + srv);
                    List<String> chans = dbManager.getChannels(srv);
                    for (String chan : chans)
                        out.println("NEW_CHANNEL:" + srv + "|" + chan);
                }

                String msg;
                while ((msg = in.readLine()) != null) {
                    System.out.println("[DEBUG] Reçu de " + username + ": " + msg);
                    if (msg.startsWith("/create_server ")) {
                        String name = msg.substring(15).trim();
                        if (dbManager.createServer(name, username)) {
                            this.sendMessage("NEW_SERVER:" + name);
                            this.sendMessage("NEW_CHANNEL:" + name + "|#general");
                        } else {
                            this.sendMessage("MSG:HOME///Système///Erreur: Nom déjà pris.");
                        }
                    } else if (msg.startsWith("/create_invite ")) {
                        String srv = msg.substring(15).trim();
                        String code = dbManager.createInvite(srv);
                        if (code != null)
                            this.sendMessage("INVITE_CODE:" + code);
                        else
                            this.sendMessage("MSG:HOME///Système///Erreur génération invitation.");
                    } else if (msg.startsWith("/join ")) {
                        String code = msg.substring(6).trim();
                        String realName = dbManager.getServerFromInvite(code);
                        if (realName != null) {
                            String joined = dbManager.joinServer(realName, username);
                            if (joined != null) {
                                this.sendMessage("NEW_SERVER:" + joined);
                                List<String> chans = dbManager.getChannels(joined);
                                for (String chan : chans)
                                    this.sendMessage("NEW_CHANNEL:" + joined + "|" + chan);
                                this.sendMessage("MSG:HOME///Système///Vous avez rejoint : " + joined);
                            } else {
                                this.sendMessage(
                                        "MSG:HOME///Système///Erreur: Impossible de rejoindre le serveur (DB Error).");
                            }
                        } else {
                            this.sendMessage("MSG:HOME///Système///Invitation invalide.");
                        }
                    } else if (msg.startsWith("/leave ")) {
                        String srv = msg.substring(7).trim();
                        if (dbManager.leaveServer(username, srv)) {
                            this.sendMessage("LEFT_SERVER:" + srv);
                            this.sendMessage("MSG:HOME///Système///Vous avez quitté " + srv);
                        }
                    }
                    // OLD JOIN SERVER (Gardé au cas où mais plus utilisé par UI)
                    else if (msg.startsWith("/join_server ")) {
                        String inputName = msg.substring(13).trim();
                        String realName = dbManager.joinServer(inputName, username);
                        if (realName != null) {
                            this.sendMessage("NEW_SERVER:" + realName);
                            List<String> chans = dbManager.getChannels(realName);
                            for (String chan : chans)
                                this.sendMessage("NEW_CHANNEL:" + realName + "|" + chan);
                            this.sendMessage("MSG:HOME///Système///Vous avez rejoint : " + realName);
                        } else {
                            this.sendMessage("MSG:HOME///Système///Serveur introuvable : " + inputName);
                        }
                    } else if (msg.startsWith("/create_channel ")) {
                        String[] parts = msg.split(" ", 3);
                        if (parts.length == 3 && dbManager.createChannel(parts[1], parts[2])) {
                            Server.broadcastSystem("NEW_CHANNEL:" + parts[1] + "|" + parts[2]);
                        }
                    } else if (msg.contains("///")) {
                        int idx = msg.indexOf("///");
                        String ctx = msg.substring(0, idx);
                        String content = msg.substring(idx + 3);

                        if (ctx.startsWith("MP:")) {
                            String target = ctx.substring(3);
                            Server.broadcastMessage(username, content, Server.getMPContext(username, target));
                        } else {
                            Server.broadcastMessage(username, content, ctx);
                        }
                    } else if (msg.startsWith("/friend add ")) {
                        String target = msg.substring(12).trim();
                        if (!target.equalsIgnoreCase(username) && dbManager.sendFriendRequest(username, target)) {
                            for (ClientHandler c : clientHandlers)
                                if (c.username.equalsIgnoreCase(target))
                                    c.sendMessage("FRIEND_REQ:" + username);
                        }
                    } else if (msg.startsWith("/friend accept ")) {
                        String req = msg.substring(15).trim();
                        dbManager.sendFriendRequest(req, username);
                        Server.sendFriendList(this);
                        for (ClientHandler c : clientHandlers)
                            if (c.username.equalsIgnoreCase(req))
                                Server.sendFriendList(c);
                    }
                }
            } catch (IOException e) {
            } finally {
                synchronized (clientHandlers) {
                    clientHandlers.remove(this);
                }
                synchronized (connectedUsers) {
                    connectedUsers.remove(this.username);
                }
                Server.broadcastUserList();
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }

        public void sendMessage(String m) {
            out.println(m);
        }
    }

    // --- SERVEUR VOCAL (UDP) ---
    private static class VoiceServer implements Runnable {
        private static final int VOICE_PORT = 1235;
        // Map: "ServerName|ChannelName" -> Liste des clients (IP:Port)
        private Map<String, Set<String>> voiceChannels = new HashMap<>();

        @Override
        public void run() {
            System.out.println(">>> Serveur Vocal (UDP) prêt sur le port " + VOICE_PORT);
            try (DatagramSocket socket = new DatagramSocket(VOICE_PORT)) {
                byte[] buffer = new byte[4096];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet); // Bloquant jusqu'à réception

                    byte[] data = packet.getData();
                    int len = packet.getLength();

                    // Extraction Header (se termine par byte 0)
                    int separatorIndex = -1;
                    for (int i = 0; i < len; i++) {
                        if (data[i] == 0) {
                            separatorIndex = i;
                            break;
                        }
                    }

                    if (separatorIndex > 0) {
                        String header = new String(data, 0, separatorIndex); // Ex: "MonServ|🔊Vocal1"
                        InetAddress clientIP = packet.getAddress();
                        int clientPort = packet.getPort();
                        String clientKey = clientIP.getHostAddress() + ":" + clientPort;

                        // Ajout du client s'il n'est pas connu
                        Set<String> participants = voiceChannels.computeIfAbsent(header, k -> new HashSet<>());
                        if (participants.add(clientKey)) {
                            // Petit log pour le debug
                            System.out.println("🎙️ Nouveau client vocal : " + clientKey + " dans " + header);
                        }

                        // Broadcast audio aux autres
                        for (String participant : participants) {
                            if (!participant.equals(clientKey)) {
                                try {
                                    // Gestion propre de l'IP et du Port
                                    int lastColon = participant.lastIndexOf(":");
                                    String ipStr = participant.substring(0, lastColon);
                                    int port = Integer.parseInt(participant.substring(lastColon + 1));

                                    InetAddress targetIP = InetAddress.getByName(ipStr);

                                    DatagramPacket forward = new DatagramPacket(data, len, targetIP, port);
                                    socket.send(forward);
                                } catch (Exception e) {
                                    System.err.println("Erreur envoi vocal vers " + participant);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}