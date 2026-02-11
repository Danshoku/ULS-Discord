import java.awt.Graphics;
import java.util.ArrayList;

public class Fantome extends Unite {

    // --- MODE JOUEUR ---
    private boolean estHumain = false;
    private int reqDX, reqDY; // Buffer de direction pour fluidité (comme Pacman)

    public Fantome(int startX, int startY) {
        super(startX, startY);
        this.dy = 1; // Par défaut, il descend
    }

    public void setEstHumain(boolean estHumain) {
        this.estHumain = estHumain;
        this.dx = 0;
        this.dy = 0;
    }

    // Méthode pour recevoir les ordres du clavier (Mode Squad)
    public void setDirection(int dx, int dy) {
        this.reqDX = dx;
        this.reqDY = dy;
    }

    // --- MOUVEMENT ---
    // Cette méthode est maintenant capable de gérer soit un Humain, soit l'IA
    @Override
    public void bouger(Carte carte) {
        if (estHumain) {
            bougerHumain(carte);
        }
        // Note: Pour l'IA, on appelle bougerIA() explicitement depuis le jeu/serveur
        // car elle a besoin de paramètres supplémentaires (target, difficulté...)
    }

    private void bougerHumain(Carte carte) {
        // Logique copiée de Pacman.java pour avoir exactement le même feeling
        if (x % Constantes.TAILLE_BLOC == 0 && y % Constantes.TAILLE_BLOC == 0) {
            // Check

            if (reqDX != 0 || reqDY != 0) {
                if (!estDansMur(x + reqDX * Constantes.VITESSE, y + reqDY * Constantes.VITESSE, carte)) {
                    dx = reqDX;
                    dy = reqDY;
                }
            }
            if (estDansMur(x + dx * Constantes.VITESSE, y + dy * Constantes.VITESSE, carte)) {
                dx = 0;
                dy = 0;
            }
        }
        x += dx * Constantes.VITESSE;
        y += dy * Constantes.VITESSE;
    }

    // --- IA (inchangée) ---
    public void bougerIA(Carte carte, Pacman pacman, boolean estSuperTupac, int niveauDifficulte) {
        if (estHumain)
            return; // Sécurité

        if (x % Constantes.TAILLE_BLOC == 0 && y % Constantes.TAILLE_BLOC == 0) {
            double chanceIntelligence = 0.0;
            if (niveauDifficulte == 0)
                chanceIntelligence = 0.20;
            else if (niveauDifficulte == 1)
                chanceIntelligence = 0.50;
            else if (niveauDifficulte == 2)
                chanceIntelligence = 0.80;
            else
                chanceIntelligence = 0.98;

            if (Math.random() < chanceIntelligence) {
                choisirDirectionIntelligente(carte, pacman, estSuperTupac);
            } else {
                choisirDirectionAleatoire(carte);
            }
        }

        int futurX = x + dx * Constantes.VITESSE;
        int futurY = y + dy * Constantes.VITESSE;

        if (estDansMur(futurX, futurY, carte)) {
            x = (x / Constantes.TAILLE_BLOC) * Constantes.TAILLE_BLOC;
            y = (y / Constantes.TAILLE_BLOC) * Constantes.TAILLE_BLOC;
            choisirDirectionAleatoire(carte);
        } else {
            x = futurX;
            y = futurY;
        }
    }

    // ... (Gardons tes méthodes privées existantes : choisirDirection...,
    // estDansMur, etc.) ...
    // COPIE ICI tes méthodes : choisirDirectionIntelligente,
    // choisirDirectionAleatoire, respawnLoin, estDansMur
    // (Je les remets pour être complet)

    private void choisirDirectionIntelligente(Carte carte, Pacman pacman, boolean fuite) {
        int caseX = x / Constantes.TAILLE_BLOC;
        int caseY = y / Constantes.TAILLE_BLOC;
        int[][] directions = { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };
        double meilleurScore = fuite ? -1 : 99999;
        int meilleurDX = 0;
        int meilleurDY = 0;
        boolean directionTrouvee = false;

        for (int[] dir : directions) {
            int testDX = dir[0];
            int testDY = dir[1];
            if (testDX == -this.dx && testDY == -this.dy)
                continue;
            if (!estDansMur(x + testDX * 24, y + testDY * 24, carte)) {
                double distance = Math.hypot((caseX + testDX) - (pacman.getX() / 24.0),
                        (caseY + testDY) - (pacman.getY() / 24.0));
                if (fuite) {
                    if (distance > meilleurScore) {
                        meilleurScore = distance;
                        meilleurDX = testDX;
                        meilleurDY = testDY;
                        directionTrouvee = true;
                    }
                } else {
                    if (distance < meilleurScore) {
                        meilleurScore = distance;
                        meilleurDX = testDX;
                        meilleurDY = testDY;
                        directionTrouvee = true;
                    }
                }
            }
        }
        if (directionTrouvee) {
            dx = meilleurDX;
            dy = meilleurDY;
        } else {
            choisirDirectionAleatoire(carte);
        }
    }

    private void choisirDirectionAleatoire(Carte carte) {
        ArrayList<String> directionsPossibles = new ArrayList<>();
        int caseX = x / Constantes.TAILLE_BLOC;
        int caseY = y / Constantes.TAILLE_BLOC;
        if (!estDansMur(x + 24, y, carte))
            directionsPossibles.add("DROITE");
        if (!estDansMur(x - 24, y, carte))
            directionsPossibles.add("GAUCHE");
        if (!estDansMur(x, y + 24, carte))
            directionsPossibles.add("BAS");
        if (!estDansMur(x, y - 24, carte))
            directionsPossibles.add("HAUT");

        if (!directionsPossibles.isEmpty()) {
            int rand = (int) (Math.random() * directionsPossibles.size());
            String choix = directionsPossibles.get(rand);
            dx = 0;
            dy = 0;
            if (choix.equals("DROITE"))
                dx = 1;
            if (choix.equals("GAUCHE"))
                dx = -1;
            if (choix.equals("BAS"))
                dy = 1;
            if (choix.equals("HAUT"))
                dy = -1;
        } else {
            dx = -dx;
            dy = -dy;
        }
    }

    public void respawnLoin(Pacman pacman, Carte carte) {
        int pacX = pacman.getX() / Constantes.TAILLE_BLOC;
        int pacY = pacman.getY() / Constantes.TAILLE_BLOC;
        int milieuX = carte.getWidth() / 2;
        int milieuY = carte.getHeight() / 2;
        int nouveauX = (pacX < milieuX) ? (carte.getWidth() - 2) : 1;
        int nouveauY = (pacY < milieuY) ? (carte.getHeight() - 2) : 1;
        this.x = nouveauX * Constantes.TAILLE_BLOC;
        this.y = nouveauY * Constantes.TAILLE_BLOC;
        this.dx = 0;
        this.dy = 0;
    }

    // Version adaptée qui prend des coordonnées précises pour vérifier les
    // collisions
    private boolean estDansMur(int px, int py, Carte carte) {
        int caseX1 = px / Constantes.TAILLE_BLOC;
        int caseY1 = py / Constantes.TAILLE_BLOC;
        int caseX2 = (px + Constantes.TAILLE_BLOC - 1) / Constantes.TAILLE_BLOC;
        int caseY2 = (py + Constantes.TAILLE_BLOC - 1) / Constantes.TAILLE_BLOC;
        if (carte.getContenu(caseX1, caseY1) == 0 || carte.getContenu(caseX2, caseY2) == 0 ||
                carte.getContenu(caseX2, caseY1) == 0 || carte.getContenu(caseX1, caseY2) == 0)
            return true;
        return false;
    }

    @Override
    public void dessiner(Graphics g) {
        g.drawImage(Constantes.IMAGE_POLICE, x, y, null);
    }
}