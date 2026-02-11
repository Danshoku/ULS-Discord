import java.io.*;
import java.net.*;
import java.util.*;

public class Serveur {

    private ArrayList<ClientHandler> clients = new ArrayList<>();
    private ModelJeu modele;
    private EtatJeu etatJeuSnapshot;
    private ServerSocket serverSocket;
    private boolean enMarche = true; // Toujours vrai pour un serveur dédié

    public static void main(String[] args) {
        new Serveur().demarrer();
    }

    public void demarrer() {
        try {
            serverSocket = new ServerSocket(Constantes.PORT);
            System.out.println("=== SERVEUR DÉDIÉ TUPAC-MAN ===");
            System.out.println("En attente sur le port " + Constantes.PORT);

            // Initialisation du moteur
            modele = new ModelJeu();
            etatJeuSnapshot = new EtatJeu();
            // Init with default, will be resized in Update if needed
            etatJeuSnapshot.grille = new int[15][15];

            // Lancement de la boucle de jeu infinie
            new Thread(new BoucleJeu()).start();

            // Lancement du Beacon UDP pour la découverte LAN
            new Thread(new UdpBeacon()).start();

            // Boucle d'acceptation des connexions (ne s'arrête jamais)
            while (enMarche) {
                try {
                    Socket socket = serverSocket.accept();

                    // Recherche d'un slot libre (0 à 7)
                    int slotLibre = -1;
                    for (int i = 0; i < Constantes.MAX_JOUEURS; i++) {
                        if (!isClientConnecte(i)) {
                            slotLibre = i;
                            break;
                        }
                    }

                    if (slotLibre != -1) {
                        System.out.println("Nouveau client connecté -> Slot " + slotLibre);
                        // Default Role = Ghost (1) to avoid Tupac conflicts
                        etatJeuSnapshot.roles[slotLibre] = 1;

                        ClientHandler h = new ClientHandler(socket, slotLibre);
                        clients.add(h);
                        new Thread(h).start();
                    } else {
                        System.out.println("Serveur plein !");
                        socket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isClientConnecte(int id) {
        for (ClientHandler c : clients)
            if (c.id == id)
                return true;
        return false;
    }

    class BoucleJeu implements Runnable {
        public void run() {
            while (enMarche) {
                try {
                    long deb = System.currentTimeMillis();

                    // Si on est en phase JEU
                    if (etatJeuSnapshot.phase == EtatJeu.PHASE_JEU) {
                        modele.update();
                        // Copie des données vers le Snapshot réseau
                        remplirSnapshot();
                    } else {
                        // Phase LOBBY
                        etatJeuSnapshot.phase = EtatJeu.PHASE_LOBBY;
                        // On met à jour la liste des joueurs actifs pour le lobby
                        for (int i = 0; i < Constantes.MAX_JOUEURS; i++)
                            etatJeuSnapshot.joueurActif[i] = isClientConnecte(i);
                    }

                    // Envoi aux clients
                    broadcast();

                    long fin = System.currentTimeMillis();
                    if (40 - (fin - deb) > 0)
                        Thread.sleep(40 - (fin - deb));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void remplirSnapshot() {
            etatJeuSnapshot.modeJeu = modele.modeJeu;
            etatJeuSnapshot.modeJeu = modele.modeJeu;
            etatJeuSnapshot.estSuperTupac = modele.estSuperTupac;
            etatJeuSnapshot.timerSuperTupac = modele.timerSuperTupac;
            etatJeuSnapshot.enAttenteDepart = modele.enAttenteDepart;
            etatJeuSnapshot.enAttenteDepart = modele.enAttenteDepart;
            etatJeuSnapshot.compteurDepart = modele.compteurDepart;

            // Check Game Over (Server Side)
            if (modele.vies <= 0 && modele.modeJeu == Constantes.MODE_MULTI) {
                etatJeuSnapshot.partieTerminee = true;
            }

            // Copie Carte (DYNAMIQUE)
            int w = modele.carte.getWidth();
            int h = modele.carte.getHeight();
            if (etatJeuSnapshot.grille.length != h || etatJeuSnapshot.grille[0].length != w) {
                etatJeuSnapshot.grille = new int[h][w];
            }

            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    etatJeuSnapshot.grille[y][x] = modele.carte.getContenu(x, y);

            // Copie Joueurs
            for (int i = 0; i < Constantes.MAX_JOUEURS; i++) {
                etatJeuSnapshot.joueurActif[i] = modele.joueurActif[i];
                etatJeuSnapshot.roles[i] = modele.roles[i];
                etatJeuSnapshot.scores[i] = modele.scores[i];
                etatJeuSnapshot.viesJoueurs[i] = modele.viesJoueurs[i]; // Fix: Sync Lives
                if (modele.joueurs[i] != null) {
                    etatJeuSnapshot.joueursX[i] = modele.joueurs[i].getX();
                    etatJeuSnapshot.joueursY[i] = modele.joueurs[i].getY();
                }
            }
            // Copie IA
            for (int i = 0; i < Constantes.MAX_JOUEURS; i++)
                etatJeuSnapshot.iaFantomesActifs[i] = false;
            for (int i = 0; i < modele.ias.size() && i < Constantes.MAX_JOUEURS; i++) {
                etatJeuSnapshot.iaFantomesActifs[i] = true;
                etatJeuSnapshot.iaFantomesX[i] = modele.ias.get(i).getX();
                etatJeuSnapshot.iaFantomesY[i] = modele.ias.get(i).getY();
            }
        }
    }

    class ClientHandler implements Runnable {
        Socket s;
        ObjectOutputStream out;
        ObjectInputStream in;
        int id;

        public ClientHandler(Socket s, int id) {
            this.s = s;
            this.id = id;
        }

        public void run() {
            try {
                out = new ObjectOutputStream(s.getOutputStream());
                in = new ObjectInputStream(s.getInputStream());
                out.writeInt(id);
                out.flush(); // On envoie son ID au joueur

                while (enMarche) {
                    int key = in.readInt();

                    // --- GESTION LOBBY ---
                    if (etatJeuSnapshot.phase == EtatJeu.PHASE_LOBBY) {
                        // Protocol:
                        // 10/32 = START (Host only)
                        // 'R' (82) = SWITCH ROLE
                        // Arrows = Change Settings (Host only)

                        if (key == 10 || key == 32) { // Enter/Space
                            if (id == 0)
                                verifierEtLancer(Constantes.MODE_MULTI);
                        }

                        if (key == 82) { // 'R' -> Switch Role
                            switchRole(id);
                        }

                        if (id == 0) { // Host Settings
                            if (key == 37) { // Left
                                etatJeuSnapshot.lobbyDifficulte = (etatJeuSnapshot.lobbyDifficulte - 1 + 4) % 4;
                            }
                            if (key == 39) { // Right
                                etatJeuSnapshot.lobbyDifficulte = (etatJeuSnapshot.lobbyDifficulte + 1) % 4;
                            }
                            // TODO: Size settings if needed (Up/Down)
                        }
                    }

                    // --- GESTION JEU ---
                    if (etatJeuSnapshot.phase == EtatJeu.PHASE_JEU) {
                        int dx = 0, dy = 0;
                        if (key == 37 || key == 81 || key == 65) // LEFT / Q / A
                            dx = -1;
                        if (key == 39 || key == 68) // RIGHT / D
                            dx = 1;
                        if (key == 38 || key == 90 || key == 87) // UP / Z / W
                            dy = -1;
                        if (key == 40 || key == 83) // DOWN / S
                            dy = 1;

                        if (dx != 0 || dy != 0)
                            modele.setInput(id, dx, dy);
                        if (key == 27) {
                        } // ECHAP
                    }
                }
            } catch (Exception e) {
                System.out.println("Client " + id + " déconnecté.");
                if (modele != null && id < modele.joueurActif.length)
                    modele.joueurActif[id] = false;
                clients.remove(this);
                if (clients.isEmpty()) {
                    System.out.println("Serveur vide. Reset complet du Lobby.");

                    // Reset Snapshot
                    etatJeuSnapshot.phase = EtatJeu.PHASE_LOBBY;
                    etatJeuSnapshot.partieTerminee = false;
                    etatJeuSnapshot.enAttenteDepart = false;
                    etatJeuSnapshot.compteurDepart = 0;
                    etatJeuSnapshot.messageInfo = "";
                    Arrays.fill(etatJeuSnapshot.roles, 0); // Reset roles or keep? defaulting all to Tupac or Ghost?
                    // Better to reset roles to clean slate.
                    // But our logic defaults new connection to Ghost.
                    // Let's just clear everything.
                    Arrays.fill(etatJeuSnapshot.joueurActif, false);
                    Arrays.fill(etatJeuSnapshot.scores, 0);
                    Arrays.fill(etatJeuSnapshot.viesJoueurs, 0);

                    // Reset Model
                    modele = new ModelJeu(); // Fresh Model
                }
            }
        }

        public void envoyer(EtatJeu e) {
            try {
                out.reset();
                out.writeObject(e);
                out.flush();
            } catch (Exception ex) {
            }
        }
    }

    private void verifierEtLancer(int mode) {
        int nbJoueurs = clients.size();
        if (nbJoueurs < 1)
            return;

        // Validation: At least 1 Tupac
        int nbTupacs = 0;
        int nbPolice = 0;
        for (int i = 0; i < Constantes.MAX_JOUEURS; i++) {
            if (isClientConnecte(i)) {
                if (etatJeuSnapshot.roles[i] == 0)
                    nbTupacs++;
                else
                    nbPolice++;
            }
        }

        if (nbTupacs == 0) {
            etatJeuSnapshot.messageInfo = "NEED TUPAC TO START!";
            broadcast();
            return;
        }

        // Validation: Ratio 1 Tupac : 3 Police
        // We fill with AI, so we just need to ensure we don't have TOO MANY human
        // police.
        // Max Police allowed = nbTupacs * 3.
        if (nbPolice > nbTupacs * 3) {
            etatJeuSnapshot.messageInfo = "TOO MANY POLICE! (Need " + (nbPolice / 3 + (nbPolice % 3 == 0 ? 0 : 1))
                    + " Tupacs)";
            broadcast();
            return;
        }

        // Validation: Max Capacity
        // Total slots needed = nbTupacs * 4.
        if (nbTupacs * 4 > Constantes.MAX_JOUEURS) {
            etatJeuSnapshot.messageInfo = "LOBBY FULL! (Max " + (Constantes.MAX_JOUEURS / 4) + " Squads)";
            broadcast();
            return;
        }

        System.out.println("Lancement de la partie ! Mode: " + mode + " avec " + nbJoueurs + " joueurs.");

        // Pass Lobby Settings to Model
        // Note: We need to pass the ROLES as well, but ModelJeu.initPartie logic needs
        // update to ACCEPT roles
        // We will update ModelJeu next. For now let's pass the basic settings.
        // Actually, ModelJeu needs to know which player has which role.
        // We will inject the roles into the model right after initPartie.

        // Copy roles from Lobby to Model BEFORE initPartie so it can use them
        for (int i = 0; i < Constantes.MAX_JOUEURS; i++) {
            modele.roles[i] = etatJeuSnapshot.roles[i];
        }

        modele.initPartie(mode, nbJoueurs, etatJeuSnapshot.lobbyDifficulte, etatJeuSnapshot.lobbyTaille == 0 ? 21 : 15,
                21); // Simplification for size
        // Re-place units based on new roles
        // We need a re-place method or just call initPartie better.
        // Let's assume initPartie does generic spawn, and we fix it later if needed.

        etatJeuSnapshot.phase = EtatJeu.PHASE_JEU;
        etatJeuSnapshot.messageInfo = "";
    }

    private void switchRole(int id) {
        int current = etatJeuSnapshot.roles[id]; // 0=Tupac, 1=Police
        int next = (current + 1) % 2;

        if (next == 0) { // Want to be Tupac
            // Check limit: 1 Tupac per 4 players (connected)
            int nbPlayers = clients.size();
            int maxTupacs = (int) Math.ceil(nbPlayers / 4.0);
            int currentTupacs = 0;
            for (int i = 0; i < Constantes.MAX_JOUEURS; i++) {
                if (isClientConnecte(i) && etatJeuSnapshot.roles[i] == 0)
                    currentTupacs++;
            }

            if (currentTupacs < maxTupacs) {
                etatJeuSnapshot.roles[id] = 0;
            } else {
                // Denied or maybe cycle? For now strictly verified.
                // Could send message "Max Tupacs Reached"
            }
        } else {
            etatJeuSnapshot.roles[id] = 1; // Can always switch to Ghost
        }
        broadcast();
    }

    private void broadcast() {
        // On utilise une copie pour éviter les erreurs si un client part pendant
        // l'envoi
        try {
            for (ClientHandler c : new ArrayList<>(clients))
                c.envoyer(etatJeuSnapshot);
        } catch (Exception e) {
        }
    }

    class UdpBeacon implements Runnable {
        public void run() {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                String msg = "TUPAC_SERVER";
                byte[] buffer = msg.getBytes();
                System.out.println("UDP Beacon started on port 9998");
                while (enMarche) {
                    try {
                        InetAddress group = InetAddress.getByName("255.255.255.255");
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, 9998);
                        socket.send(packet);
                        Thread.sleep(2000);
                    } catch (Exception e) {
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}