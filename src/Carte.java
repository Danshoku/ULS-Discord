import java.awt.Color;
import java.awt.Graphics;

public class Carte {
    // 0 = mur, 1 = point, 2 = vide, 3 = arme
    private int[][] grille;
    private int width, height;

    public Carte(int w, int h) {
        this.width = w;
        this.height = h;
        // Generate automatic maze
        GenerateurLabyrinthe gen = new GenerateurLabyrinthe(w, h);
        this.grille = gen.generer();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getContenu(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height)
            return 0;
        return grille[y][x];
    }

    public void setVide(int x, int y) {
        grille[y][x] = 2;
    }

    public void setContenu(int x, int y, int valeur) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            grille[y][x] = valeur;
        }
    }

    // Remet des points (1) partout où c'est vide (2), sauf sur les murs (0)
    public void reinitialiserPoints() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grille[y][x] == 2) {
                    grille[y][x] = 1;
                }
            }
        }
    }

    // Place l'arme au centre
    public void faireApparaitreArme() {
        int cx = width / 2;
        int cy = height / 2;
        grille[cy][cx] = 3;
    }

    public int[] getArmePosition() {
        // Optimisation: le pistolet est censé être au centre, mais on scanne au cas où
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grille[y][x] == 3)
                    return new int[] { x, y };
            }
        }
        return new int[] { width / 2, height / 2 };
    }

    public void dessiner(Graphics g) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int val = grille[y][x];
                if (val == 0) {
                    g.drawImage(Constantes.IMAGE_MUR, x * Constantes.TAILLE_BLOC, y * Constantes.TAILLE_BLOC, null);
                } else if (val == 1) {
                    g.setColor(Color.WHITE);
                    g.fillRect(x * Constantes.TAILLE_BLOC + 10, y * Constantes.TAILLE_BLOC + 10, 4, 4);
                } else if (val == 3) {
                    g.drawImage(Constantes.IMAGE_PISTOLET, x * Constantes.TAILLE_BLOC, y * Constantes.TAILLE_BLOC,
                            null);
                }
            }
        }
    }
}