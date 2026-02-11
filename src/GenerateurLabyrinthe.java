import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GenerateurLabyrinthe {
    private int width;
    private int height;
    private int[][] grille;
    private Random random;

    // Difficulty level 2-17 roughly maps to probability of checking neighbors
    // The C code used #define COMPLEXE 8
    // User requested to break MORE walls, so we decrease this value (higher chance
    // 1/N)
    private static final int COMPLEXITE = 4;

    public GenerateurLabyrinthe(int w, int h) {
        // Ensure odd dimensions for the algorithm (same as C code logic)
        this.width = (w % 2 == 0) ? w - 1 : w;
        this.height = (h % 2 == 0) ? h - 1 : h;
        this.grille = new int[height][width];
        this.random = new Random();
    }

    public int[][] generer() {
        // 1. Algo Creation Case (Kruskal / Fusoin de Zones)
        algoCreationCase();

        // 2. Complexification (Remove some extra walls)
        complexificationLaby();

        // 3. Convert Format:
        // C logic: 1=Vide, 0=Mur
        // Java logic expected: 0=Mur, 1=Point (Vide), 2=Vide (Sanctuaire), 3=Bonus
        // So we flip 0->0 (Mur) and 1->2 (Vide for now, Model will fill points)
        // Actually Model fills points later. Let's return 0 for Wall, 2 for Empty.
        // Wait, ModelJeu expect Carte format: 0=Mur, 1=Point, 2=Vide, 3=Arme
        // Let's return a grid compatible with what ModelJeu expects from previous
        // Generateur logic.
        // Previous logic returned 0=Mur, 1=Empty.
        // Let's stick to 0=Mur, 2=Empty (to be safe with Carte logic which turns 1 into
        // points)

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grille[y][x] == 1) {
                    grille[y][x] = 2; // Mark as empty space
                } else {
                    grille[y][x] = 0; // Wall
                }
            }
        }

        // Entree/Sortie logic from C code ?
        // (*tab)[1][0] = 2; //Entré Laby
        // (*tab)[*taille - 2][*taille - 1] = 3; //Sortie Laby
        // Tupac-Man doesn't strictly need entry/exit holes on border, but let's keep it
        // safe.
        // Actually let's NOT open borders to avoid escaping map.

        return grille;
    }

    // --- PORTED FROM C: algo_creation_case ---
    private void algoCreationCase() {
        // 1. Init zones and walls
        int[][] carteZones = new int[height][width];
        List<Integer> murs = new ArrayList<>();
        int nbZones = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (y % 2 == 1 && x % 2 == 1) {
                    nbZones++;
                    carteZones[y][x] = nbZones;
                    grille[y][x] = 1; // 1 = Vide in C logic context
                } else {
                    carteZones[y][x] = -1; // Mur
                    grille[y][x] = 0; // 0 = Mur in C logic context
                }

                // Collect walls between cells
                if ((y + x) % 2 == 1 && y > 0 && x > 0 && y < height - 1 && x < width - 1) {
                    murs.add(y * width + x);
                }
            }
        }

        // 2. Shuffle walls
        Collections.shuffle(murs, random);

        // 3. Merge Zones
        for (int mur : murs) {
            int y = mur / width;
            int x = mur % width;
            int zone1 = -1, zone2 = -1;

            // Identify zones separated by wall
            // Wall is either horizontal or vertical
            if (carteZones[y - 1][x] > 0 && carteZones[y + 1][x] > 0) {
                // Vertical wall separating Top and Bottom
                zone1 = carteZones[y - 1][x];
                zone2 = carteZones[y + 1][x];
            } else if (carteZones[y][x - 1] > 0 && carteZones[y][x + 1] > 0) {
                // Horizontal wall separating Left and Right
                zone1 = carteZones[y][x - 1];
                zone2 = carteZones[y][x + 1];
            } else {
                continue;
            }

            // Merge if different zones
            if (zone1 != zone2) {
                // Naive flood fill replacement (O(N^2)) - straightforward port of C code
                // For i, j in grid ...
                for (int i = 1; i < height - 1; i++) {
                    for (int j = 1; j < width - 1; j++) {
                        if (carteZones[i][j] == zone1) {
                            carteZones[i][j] = zone2; // Merge z1 into z2
                        }
                    }
                }
                grille[y][x] = 1; // Remove wall (set to Vide)
            }
        }
    }

    // --- PORTED FROM C: complexification_laby ---
    private void complexificationLaby() {
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (grille[y][x] == 0) { // If Wall
                    // Check if flanked by walls (vertical or horizontal alignment)
                    boolean horizWalls = (grille[y][x - 1] == 0 && grille[y][x + 1] == 0);
                    boolean vertWalls = (grille[y - 1][x] == 0 && grille[y + 1][x] == 0);

                    if ((horizWalls || vertWalls) && random.nextInt(COMPLEXITE) == 0) {
                        grille[y][x] = 1; // Break wall
                    }
                }
            }
        }
    }
}
