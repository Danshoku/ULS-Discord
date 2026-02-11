import java.io.Serializable;

public class EtatJeu implements Serializable {

    public static final int PHASE_LOBBY = 0;
    public static final int PHASE_JEU = 1;
    public int phase = PHASE_LOBBY;

    public int modeJeu = 1; // Par défaut Duel

    // Joueurs (Max 16)
    public int[] joueursX = new int[Constantes.MAX_JOUEURS];
    public int[] joueursY = new int[Constantes.MAX_JOUEURS];
    public boolean[] joueurActif = new boolean[Constantes.MAX_JOUEURS];
    public int[] roles = new int[Constantes.MAX_JOUEURS];
    public int[] scores = new int[Constantes.MAX_JOUEURS];
    public int[] viesJoueurs = new int[Constantes.MAX_JOUEURS]; // Vies individuelles

    // IA
    public int[] iaFantomesX = new int[Constantes.MAX_JOUEURS];
    public int[] iaFantomesY = new int[Constantes.MAX_JOUEURS];
    public boolean[] iaFantomesActifs = new boolean[Constantes.MAX_JOUEURS];

    // Carte
    public int[][] grille;

    // Etats
    public boolean estSuperTupac;
    public int timerSuperTupac;
    public boolean enAttenteDepart;
    public int compteurDepart;
    public String messageInfo = "";

    public boolean serveurFerme = false;
    public boolean partieTerminee = false;

    // Lobby Settings
    public int lobbyDifficulte = 1;
    public int lobbyTaille = 0; // 0=Full, 1=/2...

    public EtatJeu() {
    }
}