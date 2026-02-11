import java.awt.Graphics;
import java.awt.Rectangle;

public abstract class Unite {
    protected int x, y;
    protected int dx, dy;
    // NOUVEAU : Pour se souvenir du départ
    protected int startX, startY;

    public Unite(int x, int y) {
        this.x = x;
        this.y = y;
        // On sauvegarde la position initiale
        this.startX = x;
        this.startY = y;
        this.dx = 0;
        this.dy = 0;
    }

    // NOUVELLE MÉTHODE : Pour remettre l'unité au départ
    public void resetPosition() {
        this.x = startX;
        this.y = startY;
        // On arrête le mouvement
        this.dx = 0;
        this.dy = 0;
        // Si c'est un fantôme, il doit recommencer à descendre pour ne pas être bloqué
        if (this instanceof Fantome) {
            this.dy = 1;
        }
    }

    // ... le reste du fichier (méthodes abstraites, getters) ne change pas ...
    public abstract void dessiner(Graphics g);
    public abstract void bouger(Carte carte);

    public Rectangle getRect() {
        return new Rectangle(x, y, Constantes.TAILLE_BLOC - 4, Constantes.TAILLE_BLOC - 4);
    }
    public int getX() { return x; }
    public int getY() { return y; }
}