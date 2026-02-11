import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;

public class Constantes {
    public static final int TAILLE_BLOC = 24; // <--- C'est cette ligne qui semble manquer à ton compilateur
    public static final int N_BLOCS = 15;
    public static final int TAILLE_ECRAN = N_BLOCS * TAILLE_BLOC;
    public static final int VITESSE = 4;

    public static final int VIES_DEPART = 3;
    public static final int DUREE_SUPER = 250;
    public static final int DUREE_RESPAWN_ARME = 375;

    public static final int PORT = 9999;
    public static final int MAX_JOUEURS = 16;

    public static final int MODE_SOLO = 0;
    public static final int MODE_MULTI = 1;

    // --- STYLE GRAPHIQUE ---
    public static final Color COULEUR_FOND = new Color(20, 20, 30);
    public static final Color COULEUR_BOUTON = new Color(70, 70, 90);
    public static final Color COULEUR_BOUTON_ACTIF = new Color(255, 200, 0);
    public static final Font FONT_TITRE = new Font("Segoe UI", Font.BOLD, 40);
    public static final Font FONT_MENU = new Font("Segoe UI", Font.BOLD, 20);

    public static Image IMAGE_MUR;
    public static Image IMAGE_TUPAC;
    public static Image IMAGE_POLICE;
    public static Image IMAGE_PISTOLET;
    public static Image IMAGE_BACKGROUND;
    public static Image IMAGE_LOGO;

    // --- AUDIO ---
    public static javax.sound.sampled.Clip musiquearrierePlan;

    static {
        try {
            IMAGE_MUR = chargerImage("/mur.png", "resources/mur.png");
            IMAGE_TUPAC = chargerImage("/tupac.png", "resources/tupac.png");
            IMAGE_POLICE = chargerImage("/police.png", "resources/police.png");
            IMAGE_PISTOLET = chargerImage("/pistolet.png", "resources/pistolet.png");

            // Background is optional
            BufferedImage bgOrg = (BufferedImage) chargerImage("/background.png", "resources/background.png");
            IMAGE_BACKGROUND = bgOrg;

            // Logo
            BufferedImage logoOrg = (BufferedImage) chargerImage("/logo.png", "resources/logo.png");
            if (logoOrg != null) {
                IMAGE_LOGO = logoOrg.getScaledInstance(300, 200, Image.SCALE_SMOOTH);
            }

            // Scaling for game sprites (Check for null to avoid NPE if loading failed)
            if (IMAGE_MUR != null)
                IMAGE_MUR = IMAGE_MUR.getScaledInstance(TAILLE_BLOC, TAILLE_BLOC, Image.SCALE_SMOOTH);
            if (IMAGE_TUPAC != null)
                IMAGE_TUPAC = IMAGE_TUPAC.getScaledInstance(TAILLE_BLOC, TAILLE_BLOC, Image.SCALE_SMOOTH);
            if (IMAGE_POLICE != null)
                IMAGE_POLICE = IMAGE_POLICE.getScaledInstance(TAILLE_BLOC, TAILLE_BLOC, Image.SCALE_SMOOTH);
            if (IMAGE_PISTOLET != null)
                IMAGE_PISTOLET = IMAGE_PISTOLET.getScaledInstance(TAILLE_BLOC, TAILLE_BLOC, Image.SCALE_SMOOTH);

        } catch (Exception e) {
            System.err.println("ERREUR CRITIQUE : Images introuvables.");
            e.printStackTrace();
        }
    }

    private static Image chargerImage(String resourcePath, String relativePath) {
        try {
            // 1. Try Classpath (Standard JAR/IDE)
            java.net.URL url = Constantes.class.getResource(resourcePath);
            if (url != null) {
                return ImageIO.read(url);
            }

            // 2. Try Relative File (Project Root)
            System.out.println("DEBUG: Trying relative path: " + relativePath);
            java.io.File f = new java.io.File(relativePath);
            if (f.exists()) {
                System.out.println("DEBUG: Found relative: " + f.getAbsolutePath());
                return ImageIO.read(f);
            }

            // 3. Try Absolute Path (User Machine Fallback)
            String absPath = "C:/Users/coren/OneDrive - UniLaSalle/UniLaSalle/I3/Programmation/POO et Java/TP/Pac-Man A/"
                    + relativePath;
            java.io.File fAbs = new java.io.File(absPath);
            System.out.println("DEBUG: Trying absolute path: " + absPath);
            if (fAbs.exists()) {
                System.out.println("DEBUG: Found absolute: " + fAbs.getAbsolutePath());
                return ImageIO.read(fAbs);
            }

            System.err.println("ERREUR: Image introuvable partout: " + resourcePath);

        } catch (Exception e) {
            System.err.println("Echec chargement: " + resourcePath + " -> " + e.getMessage());
        }
        return null;
    }

    public static void lancerMusique(String relativePath) {
        try {
            if (musiquearrierePlan != null && musiquearrierePlan.isRunning()) {
                return; // Déjà en cours
            }

            // Si on relance, on rembobine si c'était le même, ou on reload ?
            // Simplification : On stop tout et on reload.
            arreterMusique();

            System.out.println("AUDIO: Tentative de chargement : " + relativePath);

            // 1. Try Classpath
            java.net.URL url = Constantes.class.getResource("/" + relativePath);
            if (url != null) {
                System.out.println("AUDIO: Trouvé dans le classpath : " + url);
            } else {
                System.out.println("AUDIO: Non trouvé dans le classpath (/" + relativePath + ")");
                // 2. Try Local File
                java.io.File f = new java.io.File("resources/" + relativePath);
                System.out.println("AUDIO: Recherche fichier local : " + f.getAbsolutePath());
                if (f.exists()) {
                    System.out.println("AUDIO: Fichier local trouvé !");
                    url = f.toURI().toURL();
                } else {
                    System.err.println("AUDIO: Fichier local NON trouvé.");
                }
            }

            if (url != null) {
                try {
                    javax.sound.sampled.AudioInputStream audioIn = javax.sound.sampled.AudioSystem
                            .getAudioInputStream(url);
                    javax.sound.sampled.AudioFormat format = audioIn.getFormat();
                    System.out.println("AUDIO: Format détecté : " + format.toString());

                    musiquearrierePlan = javax.sound.sampled.AudioSystem.getClip();
                    musiquearrierePlan.open(audioIn);
                    musiquearrierePlan.loop(javax.sound.sampled.Clip.LOOP_CONTINUOUSLY);
                    musiquearrierePlan.start();
                    System.out.println("AUDIO: Lecture en boucle démarrée avec succès.");
                } catch (javax.sound.sampled.UnsupportedAudioFileException uae) {
                    System.err.println(
                            "AUDIO ERREUR CRITIQUE: Le format du fichier n'est pas supporté par Java par défaut.");
                    System.err.println(
                            "AUDIO: Java Sound ne lit que le WAV/AIFF par défaut. MP3 nécessite un plugin (MP3SPI).");
                    System.err.println("SOLUTION: Convertissez 'tupac_intro.mp3' en 'tupac_intro.wav' !");
                } catch (Exception e) {
                    System.err.println("AUDIO ERREUR INCONNUE : " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.err.println("AUDIO: Fichier introuvable " + relativePath);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void arreterMusique() {
        try {
            if (musiquearrierePlan != null) {
                musiquearrierePlan.stop();
                musiquearrierePlan.close(); // Libère les ressources
                musiquearrierePlan = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}