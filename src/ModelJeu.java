import java.util.ArrayList;
import java.util.Arrays;

public class ModelJeu {

    public Carte carte;

    // Entités
    public Unite[] joueurs = new Unite[Constantes.MAX_JOUEURS];
    public boolean[] joueurActif = new boolean[Constantes.MAX_JOUEURS];
    public int[] roles = new int[Constantes.MAX_JOUEURS];
    public int[] scores = new int[Constantes.MAX_JOUEURS];
    public int[] viesJoueurs = new int[Constantes.MAX_JOUEURS]; // New: Individual Lives
    public int vies = Constantes.VIES_DEPART;

    // Les IA combleront les trous
    public ArrayList<Fantome> ias = new ArrayList<>();

    // Etats
    public boolean estSuperTupac = false;
    public int timerSuperTupac = 0;
    public boolean armeEstPrise = false;
    public int timerRespawnArme = 0;
    public boolean enAttenteDepart = false;
    public int compteurDepart = 0;
    public int modeJeu;
    public int niveauDifficulte = 1;

    public ModelJeu() {
        carte = new Carte(15, 15);
    }

    public void initPartie(int mode, int nbJoueursConnectes, int difficulte, int w, int h) {
        System.out.println("DEBUG: initPartie called with W=" + w + ", H=" + h);
        this.modeJeu = mode;
        this.niveauDifficulte = difficulte;

        // Ensure odd dimensions for maze generation
        if (w % 2 == 0)
            w--;
        if (h % 2 == 0)
            h--;

        carte = new Carte(w, h);
        carte.faireApparaitreArme();

        estSuperTupac = false;
        timerSuperTupac = 0;
        armeEstPrise = false;
        timerRespawnArme = 0;
        vies = Constantes.VIES_DEPART;
        Arrays.fill(joueurs, null);
        Arrays.fill(joueurActif, false);
        Arrays.fill(scores, 0);
        Arrays.fill(viesJoueurs, 0);
        ias.clear();

        // --- MODE SOLO (Classique) ---
        if (mode == Constantes.MODE_SOLO) {
            activerJoueur(0, 0); // J0 = Pacman

            // 1. Calculate Ghost Count (Proportional)
            int area = w * h;
            int nbFantomes = 2 + (area / 150);
            System.out.println("DEBUG: Spawning " + nbFantomes + " ghosts for area " + area);

            // 2. Spawn Ghosts RANDOMLY first
            ias.clear();
            for (int i = 0; i < nbFantomes; i++) {
                int[] pos = trouverCaseVideAleatoire();
                ajouterIA(pos[0], pos[1]);
            }

            // 3. Spawn Tupac FURTHEST from all ghosts
            // We verify distance to NEAREST ghost is maximized (MaxMin strategy)
            int[] pPos = trouverSpawnPresArme();
            placerUnite(joueurs[0], pPos[0], pPos[1]);

            lancerCompteARebours();
        }

        // --- MODE MULTI DYNAMIQUE (Unified Map) ---
        else if (mode == Constantes.MODE_MULTI) {

            // 1. Activate & Spawn Connected Players
            int nbTupacs = 0;
            int nbGhostsHuman = 0;

            for (int i = 0; i < nbJoueursConnectes; i++) {
                int r = this.roles[i]; // Presets by Serveur
                activerJoueur(i, r);

                if (r == 0) { // Tupac
                    nbTupacs++;
                    // Spawn near center
                    int cx = w / 2;
                    int cy = h / 2;
                    // Check if wall, if so spiral out
                    int[] safe = trouverCaseVideAutour(cx, cy);
                    placerUnite(joueurs[i], safe[0], safe[1]);
                } else { // Ghost
                    nbGhostsHuman++;
                    // Corners
                    int[][] coins = { { 1, 1 }, { w - 2, 1 }, { 1, h - 2 }, { w - 2, h - 2 } };
                    int[] c = coins[i % 4];
                    // Verify not wall
                    if (carte.getContenu(c[0], c[1]) == 0) {
                        // Find neighbor
                        int[] safe = trouverCaseVideAutour(c[0], c[1]);
                        placerUnite(joueurs[i], safe[0], safe[1]);
                    } else {
                        placerUnite(joueurs[i], c[0], c[1]);
                    }
                }
            }

            // 2. Fill with AI Ghosts
            // Rule: "1 tupac + 3 ghosts".
            // So Total Ghosts needed = nbTupacs * 3.
            // But if we have 0 Tupacs (should not happen in valid game?), handled
            // gracefully.
            int targetGhosts = nbTupacs * 3;
            int neededAI = targetGhosts - nbGhostsHuman;

            // Ensure at least some ghosts if no tupac? No.

            ias.clear();
            int[][] coins = { { 1, 1 }, { w - 2, 1 }, { 1, h - 2 }, { w - 2, h - 2 } };

            for (int k = 0; k < neededAI; k++) {
                int[] c = coins[k % 4];
                if (carte.getContenu(c[0], c[1]) == 0) {
                    int[] safe = trouverCaseVideAutour(c[0], c[1]);
                    ajouterIA(safe[0], safe[1]);
                } else {
                    ajouterIA(c[0], c[1]);
                }
            }
        }
    }

    private int[] trouverCaseVideAutour(int cx, int cy) {
        // Spiral search or simple radius
        for (int r = 0; r < 10; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int nx = cx + dx;
                    int ny = cy + dy;
                    int val = carte.getContenu(nx, ny);
                    // We want a safe walkable cell.
                    // 0 = Empty, 2 = Dot.
                    // Avoid 1 (Wall) and 3 (Gun).
                    if (val != 1 && val != 3)
                        return new int[] { nx, ny };
                }
            }
        }
        return new int[] { 1, 1 }; // Fallback
    }

    private void activerJoueur(int id, int role) {
        joueurActif[id] = true;
        roles[id] = role;
        if (role == 0) {
            joueurs[id] = new Pacman();
            viesJoueurs[id] = Constantes.VIES_DEPART; // Init lives
        } else {
            Fantome f = new Fantome(0, 0);
            f.setEstHumain(true);
            joueurs[id] = f;
        }
    }

    private void ajouterIA(int x, int y) {
        ias.add(new Fantome(x * 24, y * 24));
    }

    private void placerUnite(Unite u, int cx, int cy) {
        if (u == null)
            return;
        u.x = cx * 24;
        u.y = cy * 24;
        if (u instanceof Pacman)
            ((Pacman) u).annulerMouvement();
        if (u instanceof Fantome)
            ((Fantome) u).setDirection(0, 0);
    }

    public void lancerCompteARebours() {
        enAttenteDepart = true;
        compteurDepart = 100; // 100 ticks * 40ms = 4 seconds (3, 2, 1, GO)
        for (Unite u : joueurs)
            if (u instanceof Pacman)
                ((Pacman) u).annulerMouvement();
    }

    private int[] trouverCaseVideAleatoire() {
        int w = carte.getWidth();
        int h = carte.getHeight();
        for (int k = 0; k < 100; k++) {
            int rx = (int) (Math.random() * w);
            int ry = (int) (Math.random() * h);
            if (carte.getContenu(rx, ry) != 0) {
                return new int[] { rx, ry };
            }
        }
        return new int[] { 1, 1 }; // Fallback
    }

    private int[] trouverSpawnPresArme() {
        int[] arme = carte.getArmePosition();
        int ax = arme[0];
        int ay = arme[1];

        int[] best = new int[] { 1, 1 };
        double bestScore = -1;

        // On cherche une case vide dans un rayon raisonnable autour de l'arme
        // mais pas SUR l'arme si possible, et LOIN des fantômes.
        int radius = 10;

        for (int y = Math.max(1, ay - radius); y < Math.min(carte.getHeight() - 1, ay + radius); y++) {
            for (int x = Math.max(1, ax - radius); x < Math.min(carte.getWidth() - 1, ax + radius); x++) {

                int val = carte.getContenu(x, y);
                if (val != 0 && val != 3) { // Not Wall (0) and Not Gun (3)

                    // Check Occupancy by Units (Strict "Seul sur la case")
                    boolean occupied = false;

                    // Players
                    for (int i = 0; i < Constantes.MAX_JOUEURS; i++) {
                        if (joueurActif[i] && joueurs[i] != null) {
                            int px = joueurs[i].getX() / 24;
                            int py = joueurs[i].getY() / 24;
                            if (px == x && py == y) {
                                occupied = true;
                                break;
                            }
                        }
                    }
                    if (occupied)
                        continue;

                    // IAs
                    for (Fantome f : ias) {
                        int fx = f.getX() / 24;
                        int fy = f.getY() / 24;
                        if (fx == x && fy == y) {
                            occupied = true;
                            break;
                        }
                    }
                    if (occupied)
                        continue;

                    // Score logic...
                    // Score = DistanceAuxFantomes - (DistanceArme * FacteurPondération)
                    // On veut être LOIN des fantômes, mais PROCHE de l'arme.

                    double distArme = Math.hypot(x - ax, y - ay);
                    double minDistGhost = 9999;

                    for (Fantome f : ias) {
                        double d = Math.hypot(x * 24 - f.getX(), y * 24 - f.getY()) / 24.0;
                        if (d < minDistGhost)
                            minDistGhost = d;
                    }

                    // Also check distance to Human Police!
                    for (int i = 0; i < Constantes.MAX_JOUEURS; i++) {
                        if (joueurActif[i] && joueurs[i] != null && roles[i] == 1) { // Police
                            double d = Math.hypot(x * 24 - joueurs[i].getX(), y * 24 - joueurs[i].getY()) / 24.0;
                            if (d < minDistGhost)
                                minDistGhost = d;
                        }
                    }

                    // Critère de sécurité absolu : pas de spawn si fantôme < 3 cases
                    if (minDistGhost < 3)
                        continue;

                    // Score: On privilégie la sécurité, mais on veut rester "autour" de l'arme
                    // Si on est trop loin de l'arme (>8), c'est moins bien
                    double score = minDistGhost;
                    if (distArme > 8)
                        score -= 5;

                    if (score > bestScore) {
                        bestScore = score;
                        best[0] = x;
                        best[1] = y;
                    }
                }
            }
        }
        return best;
    }

    public void update() {
        if (enAttenteDepart) {
            compteurDepart--;
            if (compteurDepart <= 0)
                enAttenteDepart = false;
            return;
        }

        // 1. Déplacements Joueurs
        for (int i = 0; i < Constantes.MAX_JOUEURS; i++) {
            if (joueurActif[i] && joueurs[i] != null)
                joueurs[i].bouger(carte);
        }

        // 2. Déplacements IA
        // Chaque IA doit chasser le Tupac de sa Squad !
        // IA 0,1,2 chassent Tupac 0. IA 3,4,5 chassent Tupac 4.
        // Simplification : L'IA chasse le Tupac actif le plus proche
        for (Fantome ia : ias) {
            Pacman cible = trouverTupacPlusProche(ia);
            if (cible != null)
                ia.bougerIA(carte, cible, estSuperTupac, niveauDifficulte);
        }

        gestionTimers();
        gestionCollisions();
        verifierRespawnPoints();
    }

    private Pacman trouverTupacPlusProche(Unite chasseur) {
        Pacman best = null;
        double minDist = 9999;

        for (int i = 0; i < Constantes.MAX_JOUEURS; i += 4) { // On regarde les index 0, 4, 8 (Chefs de squad)
            if (joueurActif[i] && joueurs[i] instanceof Pacman) {
                double d = Math.hypot(chasseur.getX() - joueurs[i].getX(), chasseur.getY() - joueurs[i].getY());
                if (d < minDist) {
                    minDist = d;
                    best = (Pacman) joueurs[i];
                }
            }
        }
        return best;
    }

    public void setInput(int id, int dx, int dy) {
        if (id >= 0 && id < Constantes.MAX_JOUEURS && joueurs[id] != null) {
            if (joueurs[id] instanceof Pacman)
                ((Pacman) joueurs[id]).setDirection(dx, dy);
            else if (joueurs[id] instanceof Fantome)
                ((Fantome) joueurs[id]).setDirection(dx, dy);
        }
    }

    private void gestionTimers() {
        if (estSuperTupac) {
            timerSuperTupac--;
            if (timerSuperTupac <= 0)
                estSuperTupac = false;
        }
        if (armeEstPrise) {
            timerRespawnArme--;
            if (timerRespawnArme <= 0) {
                carte.faireApparaitreArme();
                armeEstPrise = false;
            }
        }
    }

    private void verifierRespawnPoints() {
        boolean reste = false;
        for (int y = 0; y < carte.getHeight(); y++)
            for (int x = 0; x < carte.getWidth(); x++)
                if (carte.getContenu(x, y) == 1)
                    reste = true;
        if (!reste)
            carte.reinitialiserPoints();
    }

    private void gestionCollisions() {
        for (int i = 0; i < Constantes.MAX_JOUEURS; i++) {
            if (!joueurActif[i] || joueurs[i] == null)
                continue;
            Unite u = joueurs[i];

            // --- TUPAC (Humain) ---
            if (roles[i] == 0) {
                int cx = (u.getX() + 12) / 24;
                int cy = (u.getY() + 12) / 24;
                int val = carte.getContenu(cx, cy);
                if (val == 1) {
                    carte.setVide(cx, cy);
                    scores[i] += 10;
                } else if (val == 3) {
                    carte.setVide(cx, cy);
                    scores[i] += 100;
                    activerSuperMode();
                }

                // Touche IA
                for (Fantome ia : ias) {
                    if (u.getRect().intersects(ia.getRect())) {
                        if (estSuperTupac) {
                            ia.respawnLoin((Pacman) u, carte);
                            scores[i] += 50;
                        } else
                            tuerPacman(i);
                    }
                }
            }

            // --- FANTOME (Humain) ---
            if (roles[i] == 1) {
                // Cherche si je touche un Tupac (n'importe lequel)
                for (int t = 0; t < Constantes.MAX_JOUEURS; t += 4) {
                    if (joueurActif[t] && joueurs[t] != null && u.getRect().intersects(joueurs[t].getRect())) {
                        if (estSuperTupac) {
                            placerUnite(u, 1, 1); // Je meurs
                            scores[t] += 50; // Tupac gagne pts
                        } else {
                            tuerPacman(t);
                            scores[i] += 100; // Je gagne pts
                        }
                    }
                }
            }
        }
    }

    private void activerSuperMode() {
        estSuperTupac = true;
        timerSuperTupac = Constantes.DUREE_SUPER;
        armeEstPrise = true;
        timerRespawnArme = Constantes.DUREE_RESPAWN_ARME;
    }

    private void tuerPacman(int id) {
        if (modeJeu == Constantes.MODE_SOLO) { // Changed modeJeu to mode for consistency
            viesJoueurs[id]--; // Use viesJoueurs for solo too
            if (viesJoueurs[id] > 0) {
                int[] pPos = trouverSpawnPresArme();
                placerUnite(joueurs[0], pPos[0], pPos[1]);
                for (Fantome ia : ias)
                    ia.resetPosition(); // Reset ghosts too? Or keep them? "refet un décompte à chaque mort" usually
                                        // implies safe reset. user said "reset spawn... avoid being too close".
                                        // Keeping ghosts implies dynamic danger.
                // User said: "spawn se face à coté du pistolet... eviter detre trop proche d'un
                // fantome... refet un décompte".
                // If we execute a countdown, we generally pause the game.

                lancerCompteARebours();
            } else {
                // Solo Game Over
                vies = 0; // Signal global game over
            }
        } else {
            // --- MULTIPLAYER DEATH ---
            viesJoueurs[id]--;

            if (viesJoueurs[id] > 0) {
                // Still has lives -> Respawn
                int[] safe = trouverSpawnPresArme();
                placerUnite(joueurs[id], safe[0], safe[1]);
                lancerCompteARebours();
            } else {
                // ELIMINATED
                // Do NOT set joueurActif to false, otherwise Server might drop connection or
                // logic skips.
                // Just move to void and keep "vies" at 0.
                placerUnite(joueurs[id], -100, -100); // Send to void

                // Check if ANY Tupac is still alive
                boolean teamAlive = false;
                for (int i = 0; i < Constantes.MAX_JOUEURS; i++) {
                    if (roles[i] == 0 && viesJoueurs[i] > 0) {
                        teamAlive = true;
                        break;
                    }
                }

                if (!teamAlive) {
                    // Trigger Game Over
                    // We can set a flag or just let the View handle it by checking lives?
                    // We need a server-side state for Game Over really,
                    // but for now let's set a flag "serveurFerme" or similar?
                    // actually 'vies' global var was used for Solo.
                    // Let's set global 'vies' to 0 to signal Game Over to loop.
                    vies = 0;
                }
            }
        }
    }
}