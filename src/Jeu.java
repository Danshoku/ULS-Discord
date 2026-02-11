import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class Jeu extends JPanel implements ActionListener {

    private enum Etat {
        MENU_MAIN, MENU_SERVEURS, OPTIONS, JEU_SOLO, JEU_MULTI, GAMEOVER
    }

    private Etat etatActuel = Etat.MENU_MAIN;
    private int menuSelection = 0;

    // Options
    private int optTaille = 0; // UNUSED since we auto-fit
    private int optDiff = 1; // 0=Easy, 1=Normal, 2=Hard, 3=Extreme

    // --- LISTE DES SERVEURS ---
    private ArrayList<ServerInfo> listeServeurs = new ArrayList<>();

    private ModelJeu modele;
    private Timer timerLoop;

    // --- RESEAU ---
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private EtatJeu etatRecu;
    private int monIdMulti = -1;

    public Jeu() {
        setBackground(Constantes.COULEUR_FOND);
        setFocusable(true);
        addKeyListener(new Clavier());

        modele = new ModelJeu();
        timerLoop = new Timer(40, this); // ~25 FPS

        // Init Default Server List
        listeServeurs.add(new ServerInfo("Serveur Local", "localhost", Constantes.PORT));

        // Start Network Threads
        new Thread(this::pingServeursLoop).start();
        new Thread(this::udpDiscoveryLoop).start();
    }

    // --- LOGIQUE JEU ---

    private void lancerSolo() {
        // 1. Determine dimensions based on ACTUAL component size
        int wPixels = getWidth();
        int hPixels = getHeight();

        // Fallback if not visible yet (should not happen if launched from menu)
        if (wPixels <= 0 || hPixels <= 0) {
            Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
            wPixels = d.width;
            hPixels = d.height;
        }

        // optTaille: 0=Full, 1=/2, 2=/3, 3=/4, 4=/5
        int divisor = optTaille + 1;

        int wMap = (wPixels / divisor) / Constantes.TAILLE_BLOC;
        int hMap = (hPixels / divisor) / Constantes.TAILLE_BLOC;

        // Ensure reasonable minimums
        if (wMap < 15)
            wMap = 15;
        if (hMap < 15)
            hMap = 15;

        System.out.println("DEBUG: Generation Map " + wMap + "x" + hMap + " for Screen " + wPixels + "x" + hPixels);

        modele.initPartie(Constantes.MODE_SOLO, 1, optDiff, wMap, hMap);
        etatActuel = Etat.JEU_SOLO;
        Constantes.lancerMusique("tupac_intro.wav");
        timerLoop.start();
        repaint();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (etatActuel == Etat.JEU_SOLO) {
            modele.update();
            if (modele.vies <= 0) {
                timerLoop.stop();
                Constantes.arreterMusique();
                etatActuel = Etat.GAMEOVER;
            }
            repaint();
        }
    }

    // --- RENDU GRAPHIQUE (The Big Rewrite) ---

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        // Optimized Rendering Settings
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 1. Draw Background
        drawBackground(g2);

        // 2. State-Based Rendering
        switch (etatActuel) {
            case JEU_SOLO:
            case JEU_MULTI:
                renderGame(g2);
                break;
            case MENU_MAIN:
            case OPTIONS:
            case MENU_SERVEURS:
            case GAMEOVER:
                renderMenu(g2);
                break;
        }
    }

    private void drawBackground(Graphics2D g) {
        if (Constantes.IMAGE_BACKGROUND != null) {
            g.drawImage(Constantes.IMAGE_BACKGROUND, 0, 0, getWidth(), getHeight(), null);
        } else {
            g.setColor(Constantes.COULEUR_FOND);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private void renderGame(Graphics2D g) {
        // Calculate Translation to Center the Map
        // The map is generated to pixel-perfect fit, but might have slight remainder
        int mapW, mapH;

        if (etatActuel == Etat.JEU_SOLO) {
            if (modele.carte == null)
                return;
            mapW = modele.carte.getWidth() * Constantes.TAILLE_BLOC;
            mapH = modele.carte.getHeight() * Constantes.TAILLE_BLOC;
        } else {
            // Multi check
            if (etatRecu == null || etatRecu.phase == EtatJeu.PHASE_LOBBY) {
                renderLobby(g); // Delegate to full-screen lobby render
                return;
            }

            // For Multi, we don't know exact map dimensions easily yet unless added to
            // packet
            // Assuming standard 21x21 for now or reading from grid
            // Temporarily fixed to grid size if available
            if (etatRecu.grille != null) {
                mapW = etatRecu.grille[0].length * Constantes.TAILLE_BLOC;
                mapH = etatRecu.grille.length * Constantes.TAILLE_BLOC;
            } else {
                mapW = 21 * Constantes.TAILLE_BLOC;
                mapH = 21 * Constantes.TAILLE_BLOC;
            }
        }

        int offX = (getWidth() - mapW) / 2;
        int offY = (getHeight() - mapH) / 2;

        // Prevent negative offsets
        if (offX < 0)
            offX = 0;
        if (offY < 0)
            offY = 0;

        // Apply Translation
        g.translate(offX, offY);

        // Clip to map area + HUD Footer
        Shape oldClip = g.getClip();
        g.clipRect(0, 0, mapW, mapH + 60);

        // Draw Black Background behind maze + HUD
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, mapW, mapH + 60);

        // DELEGATE DRAWING
        if (etatActuel == Etat.JEU_SOLO) {
            modele.carte.dessiner(g); // Draw Walls/Items
            drawEntitiesSolo(g); // Draw Tupac/Ghosts
            drawHUD(g, modele.scores[0], modele.viesJoueurs[0]);
        } else {
            drawMultiState(g);
            drawHUDMulti(g); // New HUD
        }

        g.setClip(oldClip);
        g.translate(-offX, -offY); // Reset for HUD/Overlays if any outside map
    }

    private void drawEntitiesSolo(Graphics2D g) {
        // Tupac
        g.drawImage(Constantes.IMAGE_TUPAC, modele.joueurs[0].getX(), modele.joueurs[0].getY(), null);

        // Ghosts
        boolean visible = true;
        if (modele.estSuperTupac && modele.timerSuperTupac < 75 && (modele.timerSuperTupac % 8) < 4) {
            visible = false; // Blinking effect
        }

        for (Fantome f : modele.ias) {
            if (!modele.estSuperTupac || visible) {
                g.drawImage(Constantes.IMAGE_POLICE, f.getX(), f.getY(), null);
            }
        }

        // --- OVERLAYS ---

        // 1. GANGSTA MODE (Centered Top)
        if (modele.estSuperTupac && visible) {
            g.setColor(Color.YELLOW);
            g.setFont(new Font("Arial", Font.BOLD, 24));
            String msg = "!!! MODE GANGSTA !!!";
            int tw = g.getFontMetrics().stringWidth(msg);
            // Draw relative to Map Center or Screen Center?
            // We are currently translated to Map coordinates.
            // Map Width is available via modele.carte
            int mapW = modele.carte.getWidth() * Constantes.TAILLE_BLOC;
            g.drawString(msg, (mapW - tw) / 2, 40);
        }

        // 2. COUNTDOWN (3, 2, 1, GO)
        if (modele.enAttenteDepart) {
            g.setColor(new Color(255, 0, 0, 200));
            g.setFont(new Font("Arial", Font.BOLD, 80));

            // 100 ticks total. 40ms per tick.
            // 100-75 = "3"
            // 75-50 = "2"
            // 50-25 = "1"
            // 25-0 = "GO!"
            String count = "";
            if (modele.compteurDepart > 75)
                count = "3";
            else if (modele.compteurDepart > 50)
                count = "2";
            else if (modele.compteurDepart > 25)
                count = "1";
            else
                count = "GO!";

            int tw = g.getFontMetrics().stringWidth(count);
            int mapW = modele.carte.getWidth() * Constantes.TAILLE_BLOC;
            int mapH = modele.carte.getHeight() * Constantes.TAILLE_BLOC;

            g.drawString(count, (mapW - tw) / 2, mapH / 2);
        }
    }

    private void drawHUDMulti(Graphics2D g) {
        if (etatRecu == null)
            return;

        int sumScore = 0;
        int myLives = -1; // -1 if not playing as Tupac

        // Calculate Score
        for (int i = 0; i < Constantes.MAX_JOUEURS; i++) {
            if (etatRecu.joueurActif[i] && etatRecu.roles[i] == 0) { // Tupac
                sumScore += etatRecu.scores[i];
            }
        }

        // Find My Lives (Direct lookup)
        if (etatRecu.roles[monIdMulti] == 0) {
            myLives = etatRecu.viesJoueurs[monIdMulti];
        }

        // Draw Centered HUD
        int w = etatRecu.grille[0].length * 24;
        int h = etatRecu.grille.length * 24;

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 18));

        // Team Score
        String sTxt = "SCORE D'ÉQUIPE : " + sumScore;
        int sw = g.getFontMetrics().stringWidth(sTxt);
        g.drawString(sTxt, (w - sw) / 2, h + 25);

        // My Lives (if Tupac)
        if (myLives != -1) {
            String vTxt = "VIES: " + myLives;
            // removed "DEAD" override per user request

            int tw = g.getFontMetrics().stringWidth(vTxt);
            g.drawString(vTxt, (w - tw) / 2, h + 50);
        } else {
            // If Ghost, maybe show "ROLE: POLICE"
            String rTxt = "RÔLE : POLICE";
            g.setColor(Color.BLUE);
            int tw = g.getFontMetrics().stringWidth(rTxt);
            g.drawString(rTxt, (w - tw) / 2, h + 50);
        }
    }

    private void drawHUD(Graphics2D g, int score, int lives) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 18));

        int w = modele.carte.getWidth() * Constantes.TAILLE_BLOC;
        int h = modele.carte.getHeight() * Constantes.TAILLE_BLOC;

        // Score: Center (Requested)
        String sTxt = "SCORE: " + score;
        int sw = g.getFontMetrics().stringWidth(sTxt);
        g.drawString(sTxt, (w - sw) / 2, h + 25);

        // Lives: Center Bottom (Requested)
        String vTxt = "VIES: " + lives;
        int tw = g.getFontMetrics().stringWidth(vTxt);
        g.drawString(vTxt, (w - tw) / 2, h + 50);
    }

    // --- MENU RENDERING SYSTEM ---
    private void renderMenu(Graphics2D g) {
        // Use a fixed virtual resolution for menus to ensure consistent layout
        // regardless of screen size. 800x600 is a safe standard.
        double virtualW = 800.0;
        double virtualH = 600.0;

        double scaleW = getWidth() / virtualW;
        double scaleH = getHeight() / virtualH;
        double scale = Math.min(scaleW, scaleH);

        int contentW = (int) (virtualW * scale);
        int contentH = (int) (virtualH * scale);
        int offX = (getWidth() - contentW) / 2;
        int offY = (getHeight() - contentH) / 2;

        g.translate(offX, offY);
        g.scale(scale, scale);

        // Clip to virtual area
        g.setClip(0, 0, (int) virtualW, (int) virtualH);

        // Draw background for menu
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, (int) virtualW, (int) virtualH);

        if (etatActuel == Etat.MENU_MAIN) {
            drawMenuMain(g, (int) virtualW, (int) virtualH);
        } else if (etatActuel == Etat.OPTIONS) {
            drawOptions(g, (int) virtualW, (int) virtualH);
        } else if (etatActuel == Etat.MENU_SERVEURS) {
            drawBrowser(g, (int) virtualW, (int) virtualH);
        } else if (etatActuel == Etat.GAMEOVER) {
            drawGameOver(g, (int) virtualW, (int) virtualH);
        }

        g.setClip(null);
        g.scale(1 / scale, 1 / scale);
        g.translate(-offX, -offY);
    }

    private void drawMenuMain(Graphics2D g, int w, int h) {
        int logoW = 350; // Reduced from 400
        int logoH = 150;
        int logoY = 30; // Moved up from 50

        if (Constantes.IMAGE_LOGO != null) {
            // Keep aspect ratio
            double ratio = (double) Constantes.IMAGE_LOGO.getHeight(null) / Constantes.IMAGE_LOGO.getWidth(null);
            logoH = (int) (logoW * ratio);
            g.drawImage(Constantes.IMAGE_LOGO, (w - logoW) / 2, logoY, logoW, logoH, null);
        } else {
            drawTitre(g, "TUPAC-MAN", 100, w);
        }

        int startY = 220; // Default fallback
        if (Constantes.IMAGE_LOGO != null)
            startY = logoY + logoH + 20; // Reduced gap from 30 to 20

        drawButton(g, "JEU SOLO", startY, menuSelection == 0, w);
        drawButton(g, "MULTIJOUEUR", startY + 65, menuSelection == 1, w); // Reduced gap 70 -> 65
        drawButton(g, "OPTIONS", startY + 130, menuSelection == 2, w);
        drawButton(g, "QUITTER", startY + 195, menuSelection == 3, w);

        g.setColor(new Color(255, 255, 255, 100));
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        g.drawString("v2.1 - Tupac Edition", 10, h - 10);
    }

    private void drawOptions(Graphics2D g, int w, int h) {
        drawTitre(g, "OPTIONS", 50, w);

        String[] diffs = { "FACILE", "NORMAL", "DIFFICILE", "EXTRÊME" };

        int cy = 200;

        g.setFont(new Font("Segoe UI", Font.BOLD, 30));
        g.setColor(menuSelection == 0 ? Constantes.COULEUR_BOUTON_ACTIF : Color.WHITE);
        String sizeText = "TAILLE : PLEIN ÉCRAN";
        if (optTaille > 0)
            sizeText = "TAILLE : PLEIN ÉCRAN / " + (optTaille + 1);

        int tw = g.getFontMetrics().stringWidth(sizeText);
        g.drawString(sizeText, (w - tw) / 2, cy);

        g.setColor(menuSelection == 1 ? Constantes.COULEUR_BOUTON_ACTIF : Color.WHITE);
        String txtDiff = "DIFFICULTÉ : < " + diffs[optDiff] + " >";
        tw = g.getFontMetrics().stringWidth(txtDiff);
        g.drawString(txtDiff, (w - tw) / 2, cy + 80);

        drawButton(g, "RETOUR", h - 100, menuSelection == 2, w);

        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("Arial", Font.ITALIC, 14));
        String help = "FLÈCHES : Changer Valeur | ENTRÉE : Sélectionner";
        tw = g.getFontMetrics().stringWidth(help);
        g.drawString(help, (w - tw) / 2, h - 30);
    }

    private void drawBrowser(Graphics2D g, int w, int h) {
        drawTitre(g, "SERVEURS", 50, w);

        int y = 120;
        if (listeServeurs.isEmpty()) {
            g.setColor(Color.WHITE);
            g.setFont(Constantes.FONT_MENU);
            String s = "Recherche de serveurs LAN...";
            g.drawString(s, (w - g.getFontMetrics().stringWidth(s)) / 2, 200);
        }

        for (int i = 0; i < listeServeurs.size(); i++) {
            ServerInfo s = listeServeurs.get(i);
            boolean sel = (i == menuSelection);

            int bw = 500;
            int bx = (w - bw) / 2;

            if (sel)
                g.setColor(new Color(255, 200, 0, 80));
            else
                g.setColor(new Color(255, 255, 255, 20));
            g.fillRoundRect(bx, y, bw, 50, 10, 10);

            if (sel) {
                g.setColor(Constantes.COULEUR_BOUTON_ACTIF);
                g.drawRoundRect(bx, y, bw, 50, 10, 10);
            }

            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, 20));
            g.drawString(s.nom, bx + 20, y + 32);
            g.setColor(Color.LIGHT_GRAY);
            g.setFont(new Font("Arial", Font.PLAIN, 14));
            g.drawString(s.ip, bx + 300, y + 32);

            g.setColor(s.online ? Color.GREEN : Color.RED);
            g.fillOval(bx + bw - 30, y + 20, 10, 10);

            y += 60;
        }

        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("Arial", Font.ITALIC, 14));
        String help = "ENTRÉE : Rejoindre | ÉCHAP : Retour";
        int tw = g.getFontMetrics().stringWidth(help);
        g.drawString(help, (w - tw) / 2, h - 30);
    }

    private void renderLobby(Graphics2D g) {
        // Reuse the text rendering logic with scaling
        double virtualW = 800.0;
        double virtualH = 600.0;

        double scaleW = getWidth() / virtualW;
        double scaleH = getHeight() / virtualH;
        double scale = Math.min(scaleW, scaleH);

        int contentW = (int) (virtualW * scale);
        int contentH = (int) (virtualH * scale);
        int offX = (getWidth() - contentW) / 2;
        int offY = (getHeight() - contentH) / 2;

        g.translate(offX, offY);
        g.scale(scale, scale);

        // Clip
        g.setClip(0, 0, (int) virtualW, (int) virtualH);

        // Draw Lobby
        drawLobbyContent(g, (int) virtualW, (int) virtualH);

        g.setClip(null);
        g.scale(1 / scale, 1 / scale);
        g.translate(-offX, -offY);
    }

    private void drawLobbyContent(Graphics2D g, int w, int h) {
        g.setColor(new Color(20, 20, 30));
        g.fillRect(0, 0, w, h);

        drawTitre(g, "SALON - " + (monIdMulti == 0 ? "HÔTE" : "INVITÉ"), 40, w);

        // --- SETTINGS (Top) ---
        g.setFont(new Font("Segoe UI", Font.BOLD, 20));
        g.setColor(Color.WHITE);
        String diffs[] = { "FACILE", "NORMAL", "DIFFICILE", "EXTRÊME" };
        String diffTxt = "DIFFICULTÉ : " + diffs[etatRecu.lobbyDifficulte];
        if (monIdMulti == 0)
            diffTxt = "< " + diffTxt + " >";
        g.drawString(diffTxt, 50, 100);

        // --- PLAYERS (List) ---
        int y = 150;
        int maxTupacs = (int) Math.ceil(getNbJoueursConnectes() / 4.0);
        g.drawString("JOUEURS (Tupacs Max : " + maxTupacs + ")", 50, y);
        y += 30;

        for (int i = 0; i < Constantes.MAX_JOUEURS; i++) {
            if (etatRecu.joueurActif[i]) {
                // Background Strip
                if (i == monIdMulti)
                    g.setColor(new Color(50, 60, 80));
                else
                    g.setColor(new Color(30, 30, 40));
                g.fillRect(50, y, 400, 30);

                // Role Icon
                int role = etatRecu.roles[i]; // 0=Tupac, 1=Police
                Image icon = (role == 0) ? Constantes.IMAGE_TUPAC : Constantes.IMAGE_POLICE;
                g.drawImage(icon, 55, y + 3, 24, 24, null);

                // Name
                g.setColor(Color.WHITE);
                g.setFont(new Font("Arial", Font.BOLD, 18));
                String name = "Joueur " + (i + 1);
                if (i == monIdMulti)
                    name += " (VOUS)";
                if (i == 0)
                    name += " [HÔTE]";
                g.drawString(name, 90, y + 22);

                // Role Text
                String rTxt = (role == 0) ? "TUPAC" : "POLICE";
                g.setColor(role == 0 ? Color.ORANGE : Color.CYAN);
                g.drawString(rTxt, 350, y + 22);

                y += 35;
            }
        }

        // --- AI BOT PREVIEW (Fill Squads) ---
        // We know server logic fills to multiples of 4.
        // We simulate this visual here.
        int connected = getNbJoueursConnectes();
        int slotsNeeded = (int) Math.ceil(Math.max(connected, 1) / 4.0) * 4;

        for (int i = connected; i < slotsNeeded; i++) {
            // Background
            g.setColor(new Color(30, 30, 40, 100));
            g.fillRect(50, y, 400, 30);

            // Icon (Police)
            g.drawImage(Constantes.IMAGE_POLICE, 55, y + 3, 24, 24, null);

            // Text
            g.setColor(Color.GRAY);
            g.setFont(new Font("Arial", Font.ITALIC, 18));
            g.drawString("IA BOT (POLICE)", 90, y + 22);

            y += 35;
        }

        // --- INSTRUCTIONS ---
        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("Arial", Font.ITALIC, 14));
        String help = "R : Changer Rôle | " + (monIdMulti == 0 ? "ENTRÉE : Lancer" : "En attente de l'hôte...");
        int tw = g.getFontMetrics().stringWidth(help);
        g.drawString(help, (w - tw) / 2, h - 30);
    }

    private int getNbJoueursConnectes() {
        if (etatRecu == null)
            return 0;
        int c = 0;
        for (boolean b : etatRecu.joueurActif)
            if (b)
                c++;
        return c;
    }

    private void drawGameOver(Graphics2D g, int w, int h) {
        drawTitre(g, "GAME OVER", h / 2 - 100, w);

        g.setColor(Color.WHITE);
        g.setFont(Constantes.FONT_MENU);

        if (etatActuel == Etat.JEU_MULTI || (etatRecu != null && (etatRecu.serveurFerme || etatRecu.partieTerminee))) {
            // Show Team Score
            // Sum from latest state
            int sum = 0;
            if (etatRecu != null) {
                for (int s : etatRecu.scores)
                    sum += s;
            }
            String s = "SCORE D'ÉQUIPE : " + sum;
            g.drawString(s, (w - g.getFontMetrics().stringWidth(s)) / 2, h / 2);
        } else {
            // Solo Score
            String s = "SCORE FINAL : " + modele.scores[0];
            g.drawString(s, (w - g.getFontMetrics().stringWidth(s)) / 2, h / 2);
        }

        String s = "Appuyez sur ESPACE pour quitter";
        g.setFont(new Font("Arial", Font.ITALIC, 20));
        g.drawString(s, (w - g.getFontMetrics().stringWidth(s)) / 2, h / 2 + 80);
    }

    // --- UTILS GRAPHICS ---
    private void drawTitre(Graphics2D g, String t, int y, int screenW) {
        g.setFont(Constantes.FONT_TITRE);
        g.setColor(Color.ORANGE);
        int w = g.getFontMetrics().stringWidth(t);
        g.drawString(t, (screenW - w) / 2 + 3, y + 3);
        g.setColor(Color.YELLOW);
        g.drawString(t, (screenW - w) / 2, y);
    }

    private void drawButton(Graphics2D g, String t, int y, boolean sel, int screenW) {
        int w = 300, h = 50;
        int x = (screenW - w) / 2;
        g.setColor(sel ? Constantes.COULEUR_BOUTON_ACTIF : Constantes.COULEUR_BOUTON);
        g.fillRoundRect(x, y, w, h, 15, 15);
        g.setColor(sel ? Color.BLACK : Color.WHITE);
        g.setFont(Constantes.FONT_MENU);
        int tw = g.getFontMetrics().stringWidth(t);
        g.drawString(t, x + (w - tw) / 2, y + 32);
    }

    private void drawMultiState(Graphics2D g) {
        if (etatRecu.grille == null)
            return;

        // Walls
        for (int y = 0; y < etatRecu.grille.length; y++) {
            for (int x = 0; x < etatRecu.grille[0].length; x++) {
                int val = etatRecu.grille[y][x];
                if (val == 0)
                    g.drawImage(Constantes.IMAGE_MUR, x * 24, y * 24, null);
                else if (val == 1) {
                    g.setColor(Color.WHITE);
                    g.fillRect(x * 24 + 10, y * 24 + 10, 4, 4);
                } else if (val == 3) {
                    g.drawImage(Constantes.IMAGE_PISTOLET, x * 24, y * 24, null);
                }
            }
        }

        // Players
        boolean visible = true;
        if (etatRecu.estSuperTupac && etatRecu.timerSuperTupac < 75 && (etatRecu.timerSuperTupac % 8) < 4) {
            visible = false; // Blinking
        }

        for (int i = 0; i < 8; i++) {
            if (etatRecu.joueurActif[i]) {
                Image img = (etatRecu.roles[i] == 0) ? Constantes.IMAGE_TUPAC : Constantes.IMAGE_POLICE;

                // If Ghost and Blinking is active -> Don't draw if not visible
                if (etatRecu.roles[i] == 1 && !visible) {
                    continue;
                }

                g.drawImage(img, etatRecu.joueursX[i], etatRecu.joueursY[i], null);
                if (i == monIdMulti) {
                    g.setColor(Color.GREEN);
                    g.drawRect(etatRecu.joueursX[i], etatRecu.joueursY[i], 24, 24);
                }
            }
        }

        // IA Ghosts (Using the separate array in EtatJeu)
        for (int i = 0; i < Constantes.MAX_JOUEURS; i++) {
            if (etatRecu.iaFantomesActifs[i]) {
                // Apply Blinking if Gangsta Mode
                if (!visible)
                    continue;

                g.drawImage(Constantes.IMAGE_POLICE, etatRecu.iaFantomesX[i], etatRecu.iaFantomesY[i], null);
            }
        }

        // GANGSTA MODE TEXT
        if (etatRecu.estSuperTupac && visible) {
            g.setColor(Color.YELLOW);
            g.setFont(new Font("Arial", Font.BOLD, 24));
            String msg = "!!! MODE GANGSTA !!!";
            int tw = g.getFontMetrics().stringWidth(msg);
            int mapW = etatRecu.grille[0].length * 24;
            g.drawString(msg, (mapW - tw) / 2, 40);
        }

        // --- COUNTDOWN OVERLAY ---
        if (etatRecu.enAttenteDepart) {
            g.setColor(new Color(255, 0, 0, 200));
            g.setFont(new Font("Arial", Font.BOLD, 80));
            String count = "";
            if (etatRecu.compteurDepart > 75)
                count = "3";
            else if (etatRecu.compteurDepart > 50)
                count = "2";
            else if (etatRecu.compteurDepart > 25)
                count = "1";
            else
                count = "GO!";

            int tw = g.getFontMetrics().stringWidth(count);
            // Center on screen (we are translated)
            // Use grid size from etatRecu
            int mapW = etatRecu.grille[0].length * 24;
            int mapH = etatRecu.grille.length * 24;

            g.drawString(count, (mapW - tw) / 2, mapH / 2);
        }
    }

    // --- CONTROLS ---

    private void rejoindreServeurSelectionne() {
        if (listeServeurs.isEmpty())
            return;
        ServerInfo s = listeServeurs.get(menuSelection);
        if (!s.online)
            return;
        try {
            socket = new Socket(s.ip, s.port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            monIdMulti = in.readInt();
            etatActuel = Etat.JEU_MULTI;
            Constantes.lancerMusique("tupac_intro.wav");

            // Send KeyListener inputs via socket
            Thread inputThread = new Thread(() -> {
                while (etatActuel == Etat.JEU_MULTI) {
                    try {
                        Thread.sleep(10);
                    } catch (Exception e) {
                    }
                    // Keys sent by KeyAdapter below
                }
            });
            inputThread.start();

            new Thread(this::boucleEcouteReseau).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendKey(int k) {
        if (out != null) {
            try {
                out.writeInt(k);
                out.flush();
            } catch (Exception e) {
            }
        }
    }

    private void boucleEcouteReseau() {
        try {
            while (etatActuel == Etat.JEU_MULTI || etatActuel == Etat.GAMEOVER) {
                etatRecu = (EtatJeu) in.readObject();
                if (etatRecu.serveurFerme)
                    throw new IOException();

                if (etatRecu.partieTerminee) {
                    // Only transition to Game Over if we are currently playing or waiting
                    if (etatActuel == Etat.JEU_MULTI) {
                        etatActuel = Etat.GAMEOVER;
                        Constantes.arreterMusique();
                    }
                }

                // Detection de transition Lobby -> Jeu
                if (etatRecu.phase == EtatJeu.PHASE_JEU && modele.modeJeu != Constantes.MODE_MULTI) {
                    // Initialisation locale si nécessaire, mais le serveur envoie tout.
                    // On fait juste repaint.
                }

                repaint();
            }
        } catch (Exception e) {
            Constantes.arreterMusique();
            quitterMulti();
        }
    }

    private void quitterMulti() {
        try {
            if (socket != null)
                socket.close();
        } catch (Exception e) {
        }
        etatActuel = Etat.MENU_MAIN;
        repaint();
    }

    // --- DISCOVERY & PING (Background) ---
    private void udpDiscoveryLoop() {
        try (DatagramSocket s = new DatagramSocket(9998)) {
            s.setBroadcast(true);
            byte[] buf = new byte[1024];
            while (true) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                s.receive(p);
                String msg = new String(p.getData(), 0, p.getLength());
                if (msg.startsWith("TUPAC_SERVER")) {
                    String ip = p.getAddress().getHostAddress();
                    boolean exists = false;
                    for (ServerInfo si : listeServeurs)
                        if (si.ip.equals(ip))
                            exists = true;
                    if (!exists) {
                        listeServeurs.add(new ServerInfo("Serveur (" + ip + ")", ip, Constantes.PORT));
                        repaint();
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private void pingServeursLoop() {
        while (true) {
            if (etatActuel == Etat.MENU_SERVEURS) {
                for (ServerInfo s : listeServeurs)
                    s.checkStatus();
                repaint();
            }
            if (etatActuel == Etat.JEU_MULTI && etatRecu != null && etatRecu.phase == EtatJeu.PHASE_LOBBY) {
                // Send keepalive or just refresh
                repaint();
            }
            try {
                Thread.sleep(2000);
            } catch (Exception e) {
            }
        }
    }

    // --- KEYBOARD ---
    private class Clavier extends KeyAdapter {
        public void keyPressed(KeyEvent e) {
            int k = e.getKeyCode();
            if (etatActuel == Etat.MENU_MAIN) {
                if (k == KeyEvent.VK_UP)
                    menuSelection--;
                if (k == KeyEvent.VK_DOWN)
                    menuSelection++;
                if (menuSelection < 0)
                    menuSelection = 3;
                if (menuSelection > 3)
                    menuSelection = 0;
                if (k == KeyEvent.VK_ENTER) {
                    if (menuSelection == 0)
                        lancerSolo();
                    if (menuSelection == 1) {
                        etatActuel = Etat.MENU_SERVEURS;
                        menuSelection = 0;
                    }
                    if (menuSelection == 2) {
                        etatActuel = Etat.OPTIONS;
                        menuSelection = 0;
                    }
                    if (menuSelection == 3) {
                        System.exit(0);
                    }
                }
            } else if (etatActuel == Etat.OPTIONS) {
                if (k == KeyEvent.VK_UP)
                    menuSelection--;
                if (k == KeyEvent.VK_DOWN)
                    menuSelection++;
                if (menuSelection < 0)
                    menuSelection = 0; // Allow selecting size
                if (menuSelection > 2)
                    menuSelection = 2; // Max Back

                if (k == KeyEvent.VK_LEFT) {
                    if (menuSelection == 0)
                        optTaille = (optTaille - 1 + 5) % 5;
                    if (menuSelection == 1)
                        optDiff = (optDiff - 1 + 4) % 4;
                }
                if (k == KeyEvent.VK_RIGHT) {
                    if (menuSelection == 0)
                        optTaille = (optTaille + 1) % 5;
                    if (menuSelection == 1)
                        optDiff = (optDiff + 1) % 4;
                }

                if (k == KeyEvent.VK_ENTER && menuSelection == 2) {
                    etatActuel = Etat.MENU_MAIN;
                    menuSelection = 0;
                }
                if (k == KeyEvent.VK_ESCAPE) {
                    etatActuel = Etat.MENU_MAIN;
                    menuSelection = 0;
                }
            } else if (etatActuel == Etat.JEU_SOLO) {
                int dx = 0, dy = 0;
                if (k == 37 || k == 81 || k == 65) // LEFT / Q / A
                    dx = -1;
                if (k == 39 || k == 68) // RIGHT / D
                    dx = 1;
                if (k == 38 || k == 90 || k == 87) // UP / Z / W
                    dy = -1;
                if (k == 40 || k == 83) // DOWN / S
                    dy = 1;
                if (dx != 0 || dy != 0)
                    modele.setInput(0, dx, dy);
                if (k == KeyEvent.VK_ESCAPE) {
                    timerLoop.stop();
                    Constantes.arreterMusique();
                    etatActuel = Etat.MENU_MAIN;
                }
            } else if (etatActuel == Etat.GAMEOVER && k == KeyEvent.VK_SPACE) {
                // Determine if we invoke quitting Multi logic
                if (socket != null && !socket.isClosed()) {
                    quitterMulti();
                } else {
                    etatActuel = Etat.MENU_MAIN;
                }
            } else if (etatActuel == Etat.MENU_SERVEURS) {
                if (k == KeyEvent.VK_ESCAPE)
                    etatActuel = Etat.MENU_MAIN;
                if (k == KeyEvent.VK_ENTER)
                    rejoindreServeurSelectionne();
                if (k == KeyEvent.VK_UP && menuSelection > 0)
                    menuSelection--;
                if (k == KeyEvent.VK_DOWN && menuSelection < listeServeurs.size() - 1)
                    menuSelection++;
            } else if (etatActuel == Etat.JEU_MULTI) {
                // Send everything to server
                sendKey(k);
                if (k == KeyEvent.VK_ESCAPE) {
                    quitterMulti();
                }
            }
            repaint();
        }
    }

    class ServerInfo {
        String nom, ip;
        int port;
        boolean online;

        public ServerInfo(String n, String i, int p) {
            nom = n;
            ip = i;
            port = p;
        }

        public void checkStatus() {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, port), 500);
                online = true;
            } catch (Exception e) {
                online = false;
            }
        }
    }
}