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

        // --- DÉBUT AJOUT : Affichage automatique de l'IP ---
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
                    // On cherche l'IPv4 locale (type 192.168.x.x)
                    if (addr instanceof Inet4Address) {
                        System.out.println("✅ IP À UTILISER SUR LE CLIENT : " + addr.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        System.out.println("Port ouvert : " + PORT);
        System.out.println("------------------------------------------------");
        // --- FIN AJOUT ---

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
        // CORRECTION : Utilisation de /// pour séparer le protocole réseau
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

                // Envois initiaux
                for (String h : dbManager.getRelevantHistory(username))
                    out.println(h);
                Server.sendFriendList(this);

                List<String> servers = dbManager.getAllServers();
                for (String srv : servers) {
                    out.println("NEW_SERVER:" + srv);
                    List<String> chans = dbManager.getChannels(srv);
                    for (String chan : chans)
                        out.println("NEW_CHANNEL:" + srv + "|" + chan);
                }

                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.startsWith("/create_server ")) {
                        String name = msg.substring(15).trim();
                        if (dbManager.createServer(name, username)) {
                            Server.broadcastSystem("NEW_SERVER:" + name);
                            Server.broadcastSystem("NEW_CHANNEL:" + name + "|#general");
                        }
                    } else if (msg.startsWith("/create_channel ")) {
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
                    } else if (msg.startsWith("/friend add ")) {
                        String target = msg.substring(12).trim();
                        if (!target.equalsIgnoreCase(username)) {
                            if (dbManager.sendFriendRequest(username, target)) {
                                for (ClientHandler c : clientHandlers) {
                                    if (c.username.equalsIgnoreCase(target))
                                        c.sendMessage("FRIEND_REQ:" + username);
                                }
                            }
                        }
                    } else if (msg.startsWith("/friend accept ")) {
                        String requester = msg.substring(15).trim();
                        dbManager.sendFriendRequest(requester, username);
                        Server.sendFriendList(this);
                        for (ClientHandler c : clientHandlers) {
                            if (c.username.equalsIgnoreCase(requester))
                                Server.sendFriendList(c);
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
            System.out.println(">>> Serveur Vocal (UDP) démarré sur le port " + VOICE_PORT);
            try (DatagramSocket socket = new DatagramSocket(VOICE_PORT)) {
                byte[] buffer = new byte[4096];

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet); // Bloquant

                    // Format du paquet : "ServerName|ChannelName///<AUDIO_DATA>"
                    // On ne peut pas facilement parser les bytes comme une string et garder l'audio
                    // intact.
                    // Protocole simplifié :
                    // Les 1024 premiers octets contiennent le header (ASCII), le reste est l'audio.
                    // OU plus simple : on parse juste le début jusqu'au séparateur, mais attention
                    // à l'intégrité des bytes audio.

                    // Pour ce prototype, on va assumer que le client envoie "JOIN:Srv|Chan" pour
                    // s'inscrire
                    // et ensuite envoie des paquets audio purs.
                    // MAIS UDP est sans état.
                    // Approche robuste : Le header est toujours présent.

                    // Approche "Quick & Dirty" viable pour un projet étudiant :
                    // On lit les X premiers octets pour trouver le channel ID.
                    // Disons que le paquet commence par une chaîne de caractères terminée par un
                    // octet 0.

                    byte[] data = packet.getData();
                    int len = packet.getLength();

                    // Extraction du Header (String)
                    int separatorIndex = -1;
                    for (int i = 0; i < len; i++) {
                        if (data[i] == 0) { // On utilise 0 (null byte) comme séparateur Header/Audio
                            separatorIndex = i;
                            break;
                        }
                    }

                    if (separatorIndex > 0) {
                        String header = new String(data, 0, separatorIndex); // Ex: "MonServ|🔊Vocal1"
                        InetAddress clientIP = packet.getAddress();
                        int clientPort = packet.getPort();
                        String clientKey = clientIP.getHostAddress() + ":" + clientPort;

                        voiceChannels.computeIfAbsent(header, k -> new HashSet<>()).add(clientKey);

                        // Broadcast aux autres membres du channel
                        Set<String> participants = voiceChannels.get(header);
                        for (String participant : participants) {
                            if (!participant.equals(clientKey)) {
                                String[] parts = participant.split(":");
                                InetAddress targetIP = InetAddress.getByName(parts[0]);
                                int targetPort = Integer.parseInt(parts[1]);

                                // On renvoie TOUT le paquet (Header + Audio) ou juste l'audio ?
                                // Mieux vaut renvoyer tel quel, le client saura filtrer.
                                DatagramPacket forward = new DatagramPacket(data, len, targetIP, targetPort);
                                socket.send(forward);
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