import java.awt.Graphics;

public class Pacman extends Unite {

    private int reqDX, reqDY; // Le fameux "Buffer"

    public Pacman() {
        // On démarre à (1, 1) pour être sûr de ne pas être dans un mur
        super(7 * Constantes.TAILLE_BLOC, 12 * Constantes.TAILLE_BLOC);
        reqDX = 0;
        reqDY = 0;
    }

    public void setDirection(int dx, int dy) {
        this.reqDX = dx;
        this.reqDY = dy;
    }

    // --- NOUVELLE MÉTHODE ---
    // Efface la mémoire de direction pour que Pacman reste immobile
    public void annulerMouvement() {
        this.dx = 0;
        this.dy = 0;
        this.reqDX = 0;
        this.reqDY = 0;
    }

    @Override
    public void bouger(Carte carte) {
        if (x % Constantes.TAILLE_BLOC == 0 && y % Constantes.TAILLE_BLOC == 0) {

            int caseX = x / Constantes.TAILLE_BLOC;
            int caseY = y / Constantes.TAILLE_BLOC;

            if (reqDX != 0 || reqDY != 0) {
                if (carte.getContenu(caseX + reqDX, caseY + reqDY) != 0) {
                    dx = reqDX;
                    dy = reqDY;
                }
            }

            if (carte.getContenu(caseX + dx, caseY + dy) == 0) {
                dx = 0;
                dy = 0;
            }
        }

        x += dx * Constantes.VITESSE;
        y += dy * Constantes.VITESSE;
    }

    @Override
    public void dessiner(Graphics g) {
        g.drawImage(Constantes.IMAGE_TUPAC, x, y, null);
    }
}