package server;

import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {
    private static final int PORT = 1234;
    private static Set<ClientHandler> clientHandlers = new HashSet<>();
    private static Set<String> connectedUsers = new HashSet<>();
    private static Map<String, String> voiceStates = new java.util.concurrent.ConcurrentHashMap<>();
    private static DatabaseManager dbManager;

    // Codes de Permissions (Bitmask)
    public static final int PERM_ADMIN = 1;
    public static final int PERM_BAN = 2;
    public static final int PERM_KICK = 4;
    public static final int PERM_CHANNELS = 8;

    // --- MODÉRATION AUTOMATIQUE : LISTE DES MOTS INTERDITS ---
    // Ajout de "ta gueule" et "putain"
    private static final List<String> BANNED_WORDS = Arrays.asList(
            "merde", "con", "connard", "salaud", "abruti", "debile", "idiot",
            "pute", "salope", "fdp", "encule", "batard", "fais chier", "nique",
            "putain", "ta gueule");

    public static void main(String[] args) {
        dbManager = new DatabaseManager();

        System.out.println(">>> Serveur Piscord (Ultimate + AutoMod V4) démarré...");
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
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String proto = "MSG:" + context + "///" + sender + "///" + content + "///" + time;
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

    public static void sendServerMembers(ClientHandler client, String serverName) {
        List<String> members = dbManager.getServerMembers(serverName);
        client.sendMessage("SERVER_MEMBERS:" + serverName + "|" + String.join(",", members));
    }

    public static String getMPContext(String u1, String u2) {
        String[] users = { u1, u2 };
        Arrays.sort(users);
        return "MP:" + users[0] + ":" + users[1];
    }

    // --- FONCTION DE DÉTECTION ---
    private static boolean containsHateSpeech(String content) {
        String normalized = content.toLowerCase();
        for (String badWord : BANNED_WORDS) {
            Pattern p = Pattern.compile("(?i)" + Pattern.quote(badWord));
            Matcher m = p.matcher(normalized);
            if (m.find()) {
                return true;
            }
        }
        return false;
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
                String pwd = in.readLine();

                if (dbManager.checkUser(u, pwd) != 0) {
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
                    Server.sendServerMembers(this, srv);
                    List<String> chans = dbManager.getChannels(srv);
                    for (String chan : chans)
                        out.println("NEW_CHANNEL:" + srv + "|" + chan);
                }

                // Send current voice states
                for (Map.Entry<String, String> entry : voiceStates.entrySet()) {
                    out.println("VOICE_STATE:" + entry.getKey() + ":" + entry.getValue());
                }

                String msg;
                while ((msg = in.readLine()) != null) {
                    System.out.println("[DEBUG] " + username + ": " + msg);

                    if (msg.startsWith("/role create ")) {
                        String[] parts = msg.split(" ");
                        if (parts.length == 5) {
                            String srv = parts[2];
                            int perms = dbManager.getUserPermissions(srv, username);
                            if (perms == 9999 || (perms & PERM_ADMIN) != 0) {
                                dbManager.createRole(srv, parts[3], Integer.parseInt(parts[4]));
                                this.sendMessage(
                                        "MSG:HOME///Système///Rôle " + parts[3] + " créé sur " + srv + " !///00:00");
                            } else {
                                this.sendMessage("MSG:HOME///Système///Erreur: Permissions insuffisantes.///00:00");
                            }
                        }
                    } else if (msg.startsWith("/role add ")) {
                        String[] parts = msg.split(" ");
                        if (parts.length == 5) {
                            String srv = parts[2];
                            int perms = dbManager.getUserPermissions(srv, username);
                            if (perms == 9999 || (perms & PERM_ADMIN) != 0) {
                                if (dbManager.assignRole(srv, parts[3], parts[4])) {
                                    this.sendMessage("MSG:HOME///Système///Rôle " + parts[4] + " donné à " + parts[3]
                                            + ".///00:00");
                                } else {
                                    this.sendMessage("MSG:HOME///Système///Erreur: Rôle introuvable.///00:00");
                                }
                            } else {
                                this.sendMessage("MSG:HOME///Système///Erreur: Permissions insuffisantes.///00:00");
                            }
                        }
                    } else if (msg.startsWith("/kick ")) {
                        String[] parts = msg.split(" ");
                        if (parts.length == 3) {
                            String srv = parts[1];
                            String target = parts[2];
                            int kickPerms = dbManager.getUserPermissions(srv, username);
                            if (kickPerms == 9999 || (kickPerms & PERM_KICK) != 0 || (kickPerms & PERM_ADMIN) != 0) {
                                if (dbManager.leaveServer(target, srv)) {
                                    Server.broadcastMessage("Système", target + " a été expulsé(e) de " + srv,
                                            srv + "|#general");
                                }
                            } else {
                                this.sendMessage(
                                        "MSG:HOME///Système///Permissions insuffisantes pour expulser.///00:00");
                            }
                        }
                    } else if (msg.startsWith("/create_server ")) {
                        String name = msg.substring(15).trim();
                        if (dbManager.createServer(name, username)) {
                            this.sendMessage("NEW_SERVER:" + name);
                            Server.sendServerMembers(this, name);
                            this.sendMessage("NEW_SERVER:" + name);
                            Server.sendServerMembers(this, name);
                            // Default Channels
                            // Default Channels
                            this.sendMessage("NEW_CHANNEL:" + name + "|#general");
                            dbManager.createChannel(name, "#general");
                        } else {
                            this.sendMessage("MSG:HOME///Système///Erreur: Nom déjà pris.///00:00");
                        }
                    } else if (msg.startsWith("/create_invite ")) {
                        String srv = msg.substring(15).trim();
                        String code = dbManager.createInvite(srv);
                        if (code != null)
                            this.sendMessage("INVITE_CODE:" + code);
                        else
                            this.sendMessage("MSG:HOME///Système///Erreur invitation.///00:00");
                    } else if (msg.startsWith("/join ")) {
                        String code = msg.substring(6).trim();
                        String realName = dbManager.getServerFromInvite(code);
                        if (realName != null) {
                            String joined = dbManager.joinServer(realName, username);
                            if (joined != null) {
                                this.sendMessage("NEW_SERVER:" + joined);
                                Server.sendServerMembers(this, joined);
                                List<String> chans = dbManager.getChannels(joined);
                                for (String chan : chans)
                                    this.sendMessage("NEW_CHANNEL:" + joined + "|" + chan);
                                this.sendMessage("MSG:HOME///Système///Bienvenue sur : " + joined + "///00:00");
                            }
                        } else {
                            this.sendMessage("MSG:HOME///Système///Invitation invalide.///00:00");
                        }
                    } else if (msg.startsWith("/join_server ")) {
                        String inputName = msg.substring(13).trim();
                        String realName = dbManager.joinServer(inputName, username);
                        if (realName != null) {
                            this.sendMessage("NEW_SERVER:" + realName);
                            Server.sendServerMembers(this, realName);
                            List<String> chans = dbManager.getChannels(realName);
                            for (String chan : chans)
                                this.sendMessage("NEW_CHANNEL:" + realName + "|" + chan);
                            this.sendMessage("MSG:HOME///Système///Bienvenue sur : " + realName + "///00:00");
                        } else {
                            this.sendMessage("MSG:HOME///Système///Serveur introuvable.///00:00");
                        }
                    } else if (msg.startsWith("/leave ")) {
                        String srv = msg.substring(7).trim();
                        if (dbManager.leaveServer(username, srv)) {
                            this.sendMessage("LEFT_SERVER:" + srv);
                            this.sendMessage("MSG:HOME///Système///Vous avez quitté " + srv + "///00:00");
                        }
                    } else if (msg.startsWith("/create_channel ")) {
                        String[] parts = msg.split(" ", 3);
                        if (parts.length == 3) {
                            String srvName = parts[1];
                            int chanPerms = dbManager.getUserPermissions(srvName, username);
                            if (chanPerms == 9999 || (chanPerms & PERM_CHANNELS) != 0
                                    || (chanPerms & PERM_ADMIN) != 0) {
                                if (dbManager.createChannel(srvName, parts[2])) {
                                    Server.broadcastSystem("NEW_CHANNEL:" + srvName + "|" + parts[2]);
                                }
                            } else {
                                this.sendMessage("MSG:HOME///Système///Permission refusée : Créer salon.///00:00");
                            }
                        }
                    } else if (msg.startsWith("RENAME_CHANNEL ")) {
                        // RENAME_CHANNEL Server|OldName|NewName
                        String[] parts = msg.substring(15).split("\\|");
                        if (parts.length == 3) {
                            String srv = parts[0];
                            String oldName = parts[1];
                            String newName = parts[2];
                            int perms = dbManager.getUserPermissions(srv, username);
                            if (perms == 9999 || (perms & PERM_CHANNELS) != 0 || (perms & PERM_ADMIN) != 0) {
                                if (dbManager.renameChannel(srv, oldName, newName)) { // Assuming dbManager has this
                                    Server.broadcastSystem("CHANNEL_RENAMED:" + srv + "|" + oldName + "|" + newName);
                                }
                            }
                        }
                    }
                    // --- GESTION DES MESSAGES (TEXTE & IMAGES) AVEC FILTRE AUTOMOD ---
                    else if (msg.contains("///")) {
                        int idx = msg.indexOf("///");
                        String ctx = msg.substring(0, idx);
                        String content = msg.substring(idx + 3);

                        // VÉRIFICATION AUTOMOD (Mots interdits)
                        if (!content.startsWith("IMG_B64:") && Server.containsHateSpeech(content)) {

                            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

                            // MODIFICATION IMPORTANTE : Retrait des balises <html> pour éviter de casser le
                            // rendu
                            // Le client ajoute déjà <html><body>...</body></html> autour de tout le chat.
                            // Ajouter un autre <html> ici créait des balises imbriquées invalides qui
                            // cachaient les messages suivants.
                            String responseContent = "<span style='color:#da373c; text-decoration:line-through;'>"
                                    + content
                                    + "</span> <span style='color:#da373c; font-weight:bold;'>(Bloqué : Propos haineux)</span>";

                            // On construit le message avec le pseudo de l'envoyeur
                            String responseMsg = "MSG:" + ctx + "///" + username + "///" + responseContent + "///"
                                    + time;

                            // On renvoie UNIQUEMENT à l'expéditeur (message bloqué pour les autres)
                            this.sendMessage(responseMsg);

                            System.out.println("[AutoMod] Message bloqué de " + username + ": " + content);
                        } else {
                            // SI LE MESSAGE EST CLEAN : On diffuse normalement
                            if (ctx.startsWith("MP:")) {
                                String target = ctx.substring(3);
                                Server.broadcastMessage(username, content, Server.getMPContext(username, target));
                            } else {
                                Server.broadcastMessage(username, content, ctx);
                            }
                        }
                    }
                    // ---------------------------------------------------------
                    else if (msg.startsWith("/friend add ")) {
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
                    } else if (msg.startsWith("VOICE_JOIN ")) {
                        String chan = msg.substring(11).trim();
                        String old = voiceStates.put(username, chan);
                        if (!chan.equals(old)) {
                            Server.broadcastSystem("VOICE_JOIN:" + username + ":" + chan);
                        }
                    } else if (msg.equals("VOICE_LEAVE")) {
                        if (voiceStates.remove(username) != null) {
                            Server.broadcastSystem("VOICE_LEAVE:" + username);
                        }
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
                if (voiceStates.remove(this.username) != null) {
                    Server.broadcastSystem("VOICE_LEAVE:" + this.username);
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
                    for (int i = 0; i < len; i++) {
                        if (data[i] == 0) {
                            separatorIndex = i;
                            break;
                        }
                    }

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
                                } catch (Exception e) {
                                    System.err.println("Erreur relais vocal");
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