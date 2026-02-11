import javax.swing.JFrame;

public class Fenetre extends JFrame {
    public Fenetre() {
        this.setTitle("Tupac-Man");
        // Fullscreen Mode
        this.setUndecorated(true);
        this.setExtendedState(JFrame.MAXIMIZED_BOTH);

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Jeu jeu = new Jeu();
        this.add(jeu);

        this.setVisible(true);
    }
}
