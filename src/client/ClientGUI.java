package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class ClientGUI extends JFrame {

    // --- CONFIGURATION ---
    private static final long MAX_IMG_SIZE = 5 * 1024 * 1024;

    // --- COULEURS ---
    private static Color DISCORD_BG = Color.decode("#313338");
    private static Color DISCORD_SIDEBAR = Color.decode("#2b2d31");
    private static Color DISCORD_SERVER_STRIP = Color.decode("#1e1f22");
    private static Color DISCORD_INPUT_BG = Color.decode("#383a40");
    private static Color TEXT_HEADER = Color.decode("#f2f3f5");
    private static Color TEXT_MUTED = Color.decode("#949ba4");
    private static Color USER_BAR_BG = Color.decode("#232428");
    private static Color VOICE_PANEL_BG = Color.decode("#232428");
    private static final Color ONLINE_GREEN = Color.decode("#23a559");
    private static final Color OFFLINE_GRAY = Color.decode("#80848E");
    private static final Color BLURPLE = Color.decode("#5865F2");
    private static final Color RED_ERROR = Color.decode("#da373c");
    // Couleur Pacman
    private static final Color PACMAN_YELLOW = Color.decode("#ffe100");

    // --- PERMISSIONS ---
    public static final int PERM_ADMIN = 1;
    public static final int PERM_BAN = 2;
    public static final int PERM_KICK = 4;
    public static final int PERM_CHANNELS = 8;

    private static final Font FONT_MAIN = new Font("Segoe UI", Font.PLAIN, 15);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 15);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 16);

    // --- VARIABLES GLOBALES ---
    private String username;
    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;

    private String activeServer = "HOME";
    private String currentContext = "HOME";

    private Map<String, List<String>> messageStorage = new HashMap<>();
    private Map<String, List<String>> serverChannels = new HashMap<>();
    private Map<String, List<String>> serverMembers = new HashMap<>();
    private Set<String> globalOnlineUsers = new HashSet<>();

    // UI Components
    private JPanel serverStrip;
    private JPanel sidebarPanel;
    private JPanel sidebarHeader;
    private JList<String> sidebarList;
    private JList<String> userList;
    private JPanel chatPanel;
    private JPanel chatHeader;
    private JTextPane chatArea;
    private JPanel inputContainer;
    private JTextField inputField;
    private JLabel chatTitleLabel;
    private JButton sidebarTitle;
    private JPanel membersPanel;
    private JButton serverSettingsBtn;
    private JPanel sidebarBottomContainer;
    private JPanel userBar;
    private JPanel voicePanel;
    private JLabel voiceChannelLabel;
    private JLabel userNameLabel;
    private JLabel userStatusLabel;
    private JButton micBtn, deafenBtn, settingsBtn;

    private boolean isMicMuted = false;
    private boolean isDeafened = false;
    private float globalVolume = 1.0f;
    private SourceDataLine currentSpeakers = null;

    private DefaultListModel<String> sidebarListModel;
    private DefaultListModel<String> friendListModel;
    private DefaultListModel<String> channelListModel;
    private DefaultListModel<String> userListModel;

    private DatagramSocket voiceSocket;
    private boolean isVoiceRunning = false;
    private String currentVoiceChannel = null;
    private Timer keepAliveTimer;

    // --- CONSTRUCTEUR PRINCIPAL ---
    public ClientGUI(Socket socket, PrintWriter out, BufferedReader in, String username) {
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.username = username;

        setTitle("Piscord - " + username);
        setSize(1000, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(DISCORD_BG);
        setLayout(new BorderLayout());

        friendListModel = new DefaultListModel<>();
        channelListModel = new DefaultListModel<>();
        userListModel = new DefaultListModel<>();

        buildInterface();

        new Thread(new IncomingReader()).start();
    }

    // ====================================================================================
    // --- METHODE POUR LANCER PAC-MAN ---
    // ====================================================================================
    private void launchPacman() {
        try {
            // Option 1 : Lancer un .jar externe (Recommandé si c'est un autre projet)
            File jarFile = new File("pacman.jar");
            if (jarFile.exists()) {
                // Lance une nouvelle JVM pour le jeu
                new ProcessBuilder("java", "-jar", "pacman.jar").start();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Le fichier 'pacman.jar' est introuvable à la racine du dossier !",
                        "Erreur Pac-Man", JOptionPane.ERROR_MESSAGE);
            }

            // Option 2 : Si la classe Pacman est DANS ce projet (src/client/Pacman.java par ex)
            // Tu pourrais juste faire : new PacmanGame().setVisible(true);

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Erreur au lancement de Pac-Man.");
        }
    }

    // ====================================================================================
    // --- FENÊTRE DE LOGIN ---
    // ====================================================================================
    public static class LoginFrame extends JFrame {
        private BufferedImage pessiImg;
        private BufferedImage sinjImg;
        private BufferedImage mainsBasImg;
        private BufferedImage logoPiscordImg;
        private BufferedImage imageMainGauche;
        private BufferedImage imageMainDroite;

        private float sinjAngle = 0.0f;
        private int animationTicks = 0;
        private float pessiLiftProgress = 0.0f;

        private JTextField ipField, userField;
        private JPasswordField passField;
        private AnimationPanel animPanel;
        private JPanel bottomPanel;

        public LoginFrame() {
            setTitle("Connexion à Piscord");
            setSize(550, 700);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new BorderLayout());
            setResizable(false);

            loadImages();

            animPanel = new AnimationPanel();
            animPanel.setBackground(Color.decode("#1a1c20"));
            add(animPanel, BorderLayout.CENTER);

            bottomPanel = new JPanel();
            bottomPanel.setBackground(DISCORD_BG);
            bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
            bottomPanel.setBorder(new EmptyBorder(20, 40, 20, 40));
            bottomPanel.setVisible(false);

            ipField = createStyledField("Adresse IP");
            ipField.setText("localhost");
            userField = createStyledField("Pseudo");
            passField = new JPasswordField();
            styleField(passField);
            passField.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(TEXT_MUTED), "Mot de passe", 0, 0, new Font("Segoe UI", Font.BOLD, 12), TEXT_HEADER));

            JButton btn = new JButton("SE CONNECTER");
            btn.setBackground(BLURPLE);
            btn.setForeground(Color.WHITE);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            btn.setFocusPainted(false);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

            btn.addActionListener(e -> attemptLogin());
            getRootPane().setDefaultButton(btn);

            bottomPanel.add(ipField);
            bottomPanel.add(Box.createVerticalStrut(10));
            bottomPanel.add(userField);
            bottomPanel.add(Box.createVerticalStrut(10));
            bottomPanel.add(passField);
            bottomPanel.add(Box.createVerticalStrut(20));
            bottomPanel.add(btn);

            add(bottomPanel, BorderLayout.SOUTH);

            new javax.swing.Timer(16, e -> {
                sinjAngle += 0.05f;
                animationTicks++;
                if (animationTicks < 120) {
                    pessiLiftProgress = (float) animationTicks / 120.0f;
                } else {
                    pessiLiftProgress = 1.0f;
                    if (!bottomPanel.isVisible()) {
                        bottomPanel.setVisible(true);
                        revalidate();
                        repaint();
                    }
                }
                animPanel.repaint();
            }).start();
        }

        private JTextField createStyledField(String title) {
            JTextField f = new JTextField();
            styleField(f);
            f.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(TEXT_MUTED), title, 0, 0, new Font("Segoe UI", Font.BOLD, 12), TEXT_HEADER));
            return f;
        }

        private void styleField(JTextField f) {
            f.setBackground(DISCORD_INPUT_BG);
            f.setForeground(Color.WHITE);
            f.setCaretColor(Color.WHITE);
            f.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        }

        private void loadImages() {
            try {
                pessiImg = loadImage("pessi.png");
                sinjImg = loadImage("image_8.png");
                mainsBasImg = loadImage("image_7.png");
                logoPiscordImg = loadImage("image_6.png");
                imageMainGauche = loadImage("nom_de_ton_image_gauche.png");
                imageMainDroite = loadImage("nom_de_ton_image_droite.png");
            } catch(Exception e) {}
        }

        private BufferedImage loadImage(String name) throws IOException {
            URL url = ClientGUI.class.getResource("/" + name);
            if (url == null) url = ClientGUI.class.getResource("/client/" + name);
            if (url != null) return ImageIO.read(url);
            File f = new File(name);
            if (f.exists()) return ImageIO.read(f);
            return null;
        }

        private class AnimationPanel extends JPanel {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int cx = w / 2;

                int pessiStartY = h - 200;
                int pessiEndY = h / 2 - 100;
                int pessiY = (int) (pessiStartY - (pessiStartY - pessiEndY) * pessiLiftProgress);
                int pessiX = cx - 75;

                if (pessiLiftProgress > 0.01) {
                    int shoulderY = h - 50;
                    int leftShoulderX = cx - 150;
                    int rightShoulderX = cx + 150;
                    int leftHandX = pessiX + 30;
                    int leftHandY = pessiY + 120;
                    int rightHandX = pessiX + 120;
                    int rightHandY = pessiY + 120;

                    g2d.setColor(new Color(230, 180, 140));
                    Stroke oldStroke = g2d.getStroke();
                    g2d.setStroke(new BasicStroke(30, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    g2d.drawLine(leftShoulderX, shoulderY, leftHandX, leftHandY + 20);
                    g2d.drawLine(rightShoulderX, shoulderY, rightHandX, rightHandY + 20);
                    g2d.setStroke(oldStroke);

                    int handSize = 70;
                    if (imageMainGauche != null) g2d.drawImage(imageMainGauche, leftShoulderX - handSize/2, shoulderY - handSize/2, handSize, handSize, null);
                    if (imageMainDroite != null) g2d.drawImage(imageMainDroite, rightShoulderX - handSize/2, shoulderY - handSize/2, handSize, handSize, null);
                }

                if (mainsBasImg != null) {
                    int mainsW = 400;
                    int mainsH = (int) ((double) mainsBasImg.getHeight() / mainsBasImg.getWidth() * mainsW);
                    g2d.drawImage(mainsBasImg, cx - mainsW / 2, h - mainsH - 20, mainsW, mainsH, null);
                }

                if (pessiImg != null) g2d.drawImage(pessiImg, pessiX, pessiY, 150, 150, null);

                if (sinjImg != null) {
                    double radius = 220;
                    int sx = (int) (cx + Math.cos(sinjAngle) * radius);
                    int sy = (int) (h/2 + Math.sin(sinjAngle) * radius);
                    double scaleX = 80.0 / sinjImg.getWidth();
                    double scaleY = 80.0 / sinjImg.getHeight();
                    AffineTransform backup = g2d.getTransform();
                    AffineTransform trans = new AffineTransform();
                    trans.translate(sx, sy);
                    trans.rotate(sinjAngle * -1.5);
                    trans.translate(-40, -40);
                    trans.scale(scaleX, scaleY);
                    g2d.drawImage(sinjImg, trans, null);
                    g2d.setTransform(backup);
                }

                if (logoPiscordImg != null) {
                    int logoW = 300;
                    int logoH = (int) ((double) logoPiscordImg.getHeight() / logoPiscordImg.getWidth() * logoW);
                    g2d.drawImage(logoPiscordImg, cx - logoW / 2, 30, logoW, logoH, null);
                }
            }
        }

        private void attemptLogin() {
            String ip = ipField.getText().trim();
            String u = userField.getText().trim();
            String pwd = new String(passField.getPassword()).trim();
            if(ip.isEmpty()) ip = "127.0.0.1";

            if (u.isEmpty() || pwd.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Veuillez remplir tous les champs.");
                return;
            }

            try {
                Socket s = new Socket(ip, 1234);
                PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

                out.println(u);
                out.println(pwd);

                String resp = in.readLine();
                if ("SUCCESS".equals(resp)) {
                    this.dispose();
                    SwingUtilities.invokeLater(() -> {
                        ClientGUI app = new ClientGUI(s, out, in, u);
                        app.setVisible(true);
                    });
                } else {
                    JOptionPane.showMessageDialog(this, "Erreur : " + resp);
                    s.close();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur connexion : " + ex.getMessage());
            }
        }
    }

    // ====================================================================================
    // --- CLIENT PRINCIPAL ---
    // ====================================================================================

    private void buildInterface() {
        serverStrip = new JPanel();
        serverStrip.setPreferredSize(new Dimension(72, 0));
        serverStrip.setBackground(DISCORD_SERVER_STRIP);
        serverStrip.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 10));

        JPanel homeBtn = createRoundButton(BLURPLE, "P", "Piscord / Amis");
        homeBtn.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { showHomeView(); } });
        serverStrip.add(homeBtn);

        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setPreferredSize(new Dimension(40, 2));
        sep.setForeground(DISCORD_SIDEBAR); sep.setBackground(DISCORD_SIDEBAR);
        serverStrip.add(sep);

        JPanel addSrv = createRoundButton(DISCORD_BG, "+", "Ajouter Serveur");
        addSrv.setBorder(BorderFactory.createLineBorder(Color.GREEN));
        addSrv.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { createServerDialog(); } });
        serverStrip.add(addSrv);

        add(serverStrip, BorderLayout.WEST);

        JPanel mainContent = new JPanel(new BorderLayout());
        add(mainContent, BorderLayout.CENTER);

        sidebarPanel = new JPanel(new BorderLayout());
        sidebarPanel.setPreferredSize(new Dimension(240, 0));
        sidebarPanel.setBackground(DISCORD_SIDEBAR);
        sidebarPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.decode("#1e1f22")));

        sidebarHeader = new JPanel(new BorderLayout());
        sidebarHeader.setBackground(DISCORD_SIDEBAR);
        sidebarHeader.setBorder(new EmptyBorder(10, 10, 10, 10));
        sidebarHeader.setPreferredSize(new Dimension(0, 50));

        sidebarTitle = new JButton("MESSAGES PRIVÉS");
        sidebarTitle.setForeground(TEXT_HEADER);
        sidebarTitle.setBackground(DISCORD_SIDEBAR);
        sidebarTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sidebarTitle.setBorder(null);
        sidebarTitle.setFocusPainted(false);
        sidebarTitle.setHorizontalAlignment(SwingConstants.LEFT);
        sidebarTitle.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sidebarTitle.addActionListener(e -> handleServerMenu());
        sidebarHeader.add(sidebarTitle, BorderLayout.CENTER);

        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        headerActions.setOpaque(false);

        serverSettingsBtn = new JButton("⚙");
        serverSettingsBtn.setFont(new Font("Segoe UI Emoji", Font.BOLD, 16));
        serverSettingsBtn.setForeground(TEXT_MUTED);
        serverSettingsBtn.setContentAreaFilled(false);
        serverSettingsBtn.setBorder(null);
        serverSettingsBtn.setFocusPainted(false);
        serverSettingsBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        serverSettingsBtn.setToolTipText("Paramètres du Serveur (Rôles)");
        serverSettingsBtn.setVisible(false);
        serverSettingsBtn.addActionListener(e -> openRoleManager());

        JButton addActionBtn = new JButton("+");
        addActionBtn.setBackground(ONLINE_GREEN);
        addActionBtn.setForeground(Color.WHITE);
        addActionBtn.setFocusPainted(false);
        addActionBtn.setBorder(new EmptyBorder(2, 8, 2, 8));
        addActionBtn.addActionListener(e -> handleAddAction());

        headerActions.add(serverSettingsBtn);
        headerActions.add(addActionBtn);
        sidebarHeader.add(headerActions, BorderLayout.EAST);

        sidebarPanel.add(sidebarHeader, BorderLayout.NORTH);

        sidebarListModel = friendListModel;
        sidebarList = new JList<>(sidebarListModel);
        sidebarList.setBackground(DISCORD_SIDEBAR);
        sidebarList.setForeground(TEXT_MUTED);
        sidebarList.setFont(FONT_MAIN);
        sidebarList.setCellRenderer(new PaddingRenderer(10));
        sidebarList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { handleSidebarClick(); }
        });
        sidebarPanel.add(new JScrollPane(sidebarList), BorderLayout.CENTER);

        sidebarBottomContainer = new JPanel(new BorderLayout());
        sidebarBottomContainer.setBackground(DISCORD_SIDEBAR);
        buildVoicePanel();
        sidebarBottomContainer.add(voicePanel, BorderLayout.NORTH);
        buildUserBar();
        sidebarBottomContainer.add(userBar, BorderLayout.SOUTH);
        sidebarPanel.add(sidebarBottomContainer, BorderLayout.SOUTH);
        mainContent.add(sidebarPanel, BorderLayout.WEST);

        chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(DISCORD_BG);

        chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(DISCORD_BG);
        chatHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.decode("#26272d")));
        chatHeader.setPreferredSize(new Dimension(0, 50));

        chatTitleLabel = new JLabel(" Accueil");
        chatTitleLabel.setForeground(TEXT_HEADER);
        chatTitleLabel.setFont(FONT_TITLE);
        chatTitleLabel.setBorder(new EmptyBorder(0, 20, 0, 0));
        chatHeader.add(chatTitleLabel, BorderLayout.CENTER);

        chatPanel.add(chatHeader, BorderLayout.NORTH);

        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setContentType("text/html");
        chatArea.setBackground(DISCORD_BG);

        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("body { font-family: Segoe UI, sans-serif; font-size: 12px; color: #f2f3f5; background-color: #313338; margin: 5px; }");
        styleSheet.addRule("a { color: #00b0f4; text-decoration: none; }");
        chatArea.setEditorKit(kit);

        chatArea.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try { Desktop.getDesktop().browse(e.getURL().toURI()); } catch (Exception ex) {}
            }
        });

        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        inputContainer = new JPanel(new BorderLayout());
        inputContainer.setBackground(DISCORD_BG);
        inputContainer.setBorder(new EmptyBorder(20, 20, 20, 20));

        JButton uploadBtn = new JButton("📎");
        uploadBtn.setBackground(DISCORD_INPUT_BG);
        uploadBtn.setForeground(TEXT_MUTED);
        uploadBtn.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        uploadBtn.setFocusPainted(false);
        uploadBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        uploadBtn.addActionListener(e -> sendImage());

        inputField = new JTextField();
        inputField.setBackground(DISCORD_INPUT_BG);
        inputField.setForeground(TEXT_HEADER);
        inputField.setCaretColor(TEXT_HEADER);
        inputField.setFont(FONT_MAIN);
        inputField.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        inputField.addActionListener(e -> sendMessage());

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V) {
                    boolean handled = pasteImageFromClipboard();
                    if (handled) e.consume();
                }
            }
        });

        inputContainer.add(uploadBtn, BorderLayout.WEST);
        inputContainer.add(inputField, BorderLayout.CENTER);
        chatPanel.add(inputContainer, BorderLayout.SOUTH);

        mainContent.add(chatPanel, BorderLayout.CENTER);

        membersPanel = new JPanel(new BorderLayout());
        membersPanel.setPreferredSize(new Dimension(240, 0));
        membersPanel.setBackground(DISCORD_SIDEBAR);

        JLabel membersTitle = new JLabel(" MEMBRES");
        membersTitle.setForeground(TEXT_MUTED);
        membersTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        membersTitle.setBorder(new EmptyBorder(20, 15, 10, 0));
        membersPanel.add(membersTitle, BorderLayout.NORTH);

        userList = new JList<>(userListModel);
        userList.setBackground(DISCORD_SIDEBAR);
        userList.setCellRenderer(new DiscordMemberRenderer());
        membersPanel.add(new JScrollPane(userList), BorderLayout.CENTER);

        // --- NOUVEAU BOUTON PAC-MAN EN BAS À DROITE ---
        JPanel gamePanel = new JPanel(new BorderLayout());
        gamePanel.setBackground(DISCORD_SIDEBAR);
        gamePanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JButton pacmanBtn = new JButton("ᗧ••• PAC-MAN");
        pacmanBtn.setBackground(PACMAN_YELLOW);
        pacmanBtn.setForeground(Color.BLACK);
        pacmanBtn.setFont(new Font("Segoe UI Black", Font.BOLD, 14));
        pacmanBtn.setFocusPainted(false);
        pacmanBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        pacmanBtn.addActionListener(e -> launchPacman());

        gamePanel.add(pacmanBtn, BorderLayout.CENTER);
        membersPanel.add(gamePanel, BorderLayout.SOUTH);
        // ----------------------------------------------

        mainContent.add(membersPanel, BorderLayout.EAST);
    }

    // --- GESTION IMAGES ---
    private boolean pasteImageFromClipboard() {
        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t == null) return false;
            if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
                processClipboardImage(img);
                return true;
            } else if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) t.getTransferData(DataFlavor.stringFlavor);
                if (text != null && text.startsWith("data:image")) {
                    int commaIndex = text.indexOf(",");
                    if (commaIndex != -1) {
                        String base64 = text.substring(commaIndex + 1);
                        sendBase64Image(base64);
                        return true;
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    private void processClipboardImage(Image img) {
        try {
            BufferedImage bi;
            if (img instanceof BufferedImage) bi = (BufferedImage) img;
            else {
                bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                Graphics g = bi.createGraphics(); g.drawImage(img, 0, 0, null); g.dispose();
            }
            File tempFile = File.createTempFile("paste_clip", ".png");
            ImageIO.write(bi, "png", tempFile);
            processAndSendImage(tempFile);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendImage() {
        if (currentContext.equals("HOME")) { JOptionPane.showMessageDialog(this, "Sélectionnez d'abord un salon ou un ami."); return; }
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Envoyer une image");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Images (JPG, PNG, GIF)", "jpg", "png", "gif", "jpeg"));
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            processAndSendImage(fileChooser.getSelectedFile());
        }
    }

    private void processAndSendImage(File file) {
        if (file.length() > MAX_IMG_SIZE) { JOptionPane.showMessageDialog(this, "L'image est trop lourde (>5Mo)."); return; }
        try {
            byte[] fileContent = java.nio.file.Files.readAllBytes(file.toPath());
            String encoded = Base64.getEncoder().encodeToString(fileContent);
            encoded = encoded.replaceAll("\\s", "");
            sendBase64Image(encoded);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void sendBase64Image(String b64) {
        String protocolMsg = "IMG_B64:" + b64;
        out.println(currentContext + "///" + protocolMsg);
    }

    private void sendMessage() {
        String msg = inputField.getText().trim();
        if (!msg.isEmpty() && out != null) {
            if (currentContext.equals("HOME")) { JOptionPane.showMessageDialog(this, "Sélectionnez un ami ou un salon !"); return; }
            if (msg.startsWith("data:image")) {
                try {
                    String base64 = msg.substring(msg.indexOf(",") + 1);
                    sendBase64Image(base64);
                } catch (Exception e) { JOptionPane.showMessageDialog(this, "Format d'image invalide."); }
            } else {
                out.println(currentContext + "///" + msg);
            }
            inputField.setText("");
        }
    }

    private void updateChatDisplay() {
        if (currentContext.startsWith("MP:")) {
            String target = currentContext.substring(3);
            List<String> rawMessages = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : messageStorage.entrySet()) {
                if (entry.getKey().startsWith("MP:") && entry.getKey().contains(target))
                    rawMessages.addAll(entry.getValue());
            }
            renderHTMLMessages(rawMessages);
        } else {
            List<String> msgs = messageStorage.getOrDefault(currentContext, new ArrayList<>());
            renderHTMLMessages(msgs);
        }
    }

    private void renderHTMLMessages(List<String> messages) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        for (String raw : messages) {
            int firstColon = raw.indexOf(":");
            int secondColon = raw.indexOf(":", firstColon + 1);
            if (secondColon != -1) {
                String meta = raw.substring(0, secondColon);
                String content = raw.substring(secondColon + 1).trim();
                html.append("<div style='margin-bottom:5px;'>");
                html.append("<b><span style='color:#dbdee1;'>").append(meta).append("</span></b>: ");

                if (content.startsWith("IMG_B64:")) {
                    try {
                        String b64 = content.substring(8);
                        File tempImg = writeBase64ToTempFile(b64);
                        if (tempImg != null) {
                            String path = tempImg.toURI().toString();
                            html.append("<br><img src='").append(path).append("' width='350'><br>");
                        } else { html.append("[Image Corrompue]"); }
                    } catch (Exception e) { html.append("[Erreur Image]"); }
                } else {
                    String processed = processTextForLinks(content);
                    html.append("<span style='color:#dbdee1;'>").append(processed).append("</span>");
                }
                html.append("</div>");
            }
        }
        html.append("</body></html>");
        chatArea.setText(html.toString());
        SwingUtilities.invokeLater(() -> chatArea.setCaretPosition(chatArea.getDocument().getLength()));
    }

    private Map<Integer, File> imageCache = new HashMap<>();

    private File writeBase64ToTempFile(String b64) {
        int hash = b64.hashCode();
        if (imageCache.containsKey(hash)) return imageCache.get(hash);
        try {
            byte[] data = Base64.getDecoder().decode(b64);
            File temp = File.createTempFile("piscord_img_" + hash, ".png");
            temp.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(temp)) { fos.write(data); }
            imageCache.put(hash, temp);
            return temp;
        } catch (Exception e) { return null; }
    }

    private String processTextForLinks(String text) {
        String urlRegex = "(https?://\\S+\\.(png|jpg|jpeg|gif))";
        String simpleUrlRegex = "(https?://\\S+)";
        Pattern imgPat = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE);
        Matcher imgMat = imgPat.matcher(text);
        if (imgMat.find()) { return imgMat.replaceAll("<br><img src='$1' width='200'><br>"); }
        Pattern urlPat = Pattern.compile(simpleUrlRegex, Pattern.CASE_INSENSITIVE);
        Matcher urlMat = urlPat.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (urlMat.find()) { urlMat.appendReplacement(sb, "<a href='$1'>$1</a>"); }
        urlMat.appendTail(sb);
        return sb.toString();
    }

    private class IncomingReader implements Runnable {
        public void run() {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.startsWith("NEW_SERVER:")) { String finalMsg = msg; SwingUtilities.invokeLater(() -> addServerIcon(finalMsg.substring(11))); }
                    else if (msg.startsWith("NEW_CHANNEL:")) {
                        String[] p = msg.substring(12).split("\\|");
                        serverChannels.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p[1]);
                        if (activeServer.equals(p[0])) SwingUtilities.invokeLater(() -> {
                            channelListModel.clear(); for (String c : serverChannels.get(p[0])) channelListModel.addElement(c);
                        });
                    }
                    else if (msg.startsWith("MSG:") || msg.startsWith("HISTORY:")) {
                        String payload = msg.contains("MSG:") ? msg.substring(4) : msg.substring(8);
                        String[] parts = payload.split("///");
                        if (parts.length >= 4) {
                            String ctx = parts[0], snd = parts[1], cnt = parts[2], time = parts[3];
                            messageStorage.computeIfAbsent(ctx, k -> new ArrayList<>()).add("[" + time + "] " + snd + ": " + cnt);
                            if (currentContext.equals(ctx) || (currentContext.startsWith("MP:") && ctx.contains(username))) SwingUtilities.invokeLater(() -> updateChatDisplay());
                        }
                    }
                    else if (msg.startsWith("FRIENDLIST:")) {
                        String raw = msg.substring(11);
                        if (!raw.isEmpty()) SwingUtilities.invokeLater(() -> { friendListModel.clear(); for(String f : raw.split(",")) friendListModel.addElement(f); });
                    }
                    else if (msg.startsWith("USERLIST:")) {
                        String[] users = msg.equals("USERLIST:") ? new String[0] : msg.substring(9).split(",");
                        globalOnlineUsers.clear(); Collections.addAll(globalOnlineUsers, users);
                        SwingUtilities.invokeLater(() -> updateUserList());
                    }
                    else if (msg.startsWith("SERVER_MEMBERS:")) {
                        String[] p = msg.substring(15).split("\\|");
                        if (p.length == 2) { serverMembers.put(p[0], Arrays.asList(p[1].split(","))); if (activeServer.equals(p[0])) SwingUtilities.invokeLater(() -> updateUserList()); }
                    }
                }
            } catch (IOException e) {}
        }
    }

    private void buildVoicePanel() {
        voicePanel = new JPanel(new BorderLayout());
        voicePanel.setBackground(VOICE_PANEL_BG);
        voicePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, Color.decode("#1e1f22")),
                new EmptyBorder(8, 10, 8, 10)
        ));
        voicePanel.setVisible(false);
        JPanel infoP = new JPanel(new GridLayout(2, 1));
        infoP.setOpaque(false);
        JLabel connectedLabel = new JLabel("Voix connectée");
        connectedLabel.setForeground(ONLINE_GREEN);
        connectedLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        voiceChannelLabel = new JLabel("Salon Vocal");
        voiceChannelLabel.setForeground(TEXT_MUTED);
        voiceChannelLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        infoP.add(connectedLabel);
        infoP.add(voiceChannelLabel);
        JButton hangupBtn = new JButton("📞");
        hangupBtn.setFont(new Font("Segoe UI Emoji", Font.BOLD, 16));
        hangupBtn.setForeground(TEXT_HEADER);
        hangupBtn.setBackground(VOICE_PANEL_BG);
        hangupBtn.setBorderPainted(false);
        hangupBtn.setFocusPainted(false);
        hangupBtn.setContentAreaFilled(false);
        hangupBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        hangupBtn.setToolTipText("Déconnecter");
        hangupBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { hangupBtn.setOpaque(true); hangupBtn.setBackground(RED_ERROR); }
            public void mouseExited(MouseEvent e) { hangupBtn.setOpaque(false); hangupBtn.setBackground(VOICE_PANEL_BG); }
        });
        hangupBtn.addActionListener(e -> leaveVoiceChannel());
        voicePanel.add(infoP, BorderLayout.CENTER);
        voicePanel.add(hangupBtn, BorderLayout.EAST);
    }

    private void buildUserBar() {
        userBar = new JPanel(new BorderLayout());
        userBar.setBackground(USER_BAR_BG);
        userBar.setPreferredSize(new Dimension(0, 52));
        userBar.setBorder(new EmptyBorder(5, 10, 5, 10));
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        infoPanel.setOpaque(false);
        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ONLINE_GREEN); g2.fillOval(0, 0, 32, 32);
                g2.setColor(USER_BAR_BG); g2.fillOval(22, 22, 12, 12);
                g2.setColor(ONLINE_GREEN); g2.fillOval(24, 24, 8, 8);
            }
        };
        avatar.setPreferredSize(new Dimension(32, 32)); avatar.setOpaque(false);
        JPanel textPanel = new JPanel(new GridLayout(2, 1));
        textPanel.setOpaque(false);
        userNameLabel = new JLabel("Chargement...");
        userNameLabel.setForeground(TEXT_HEADER);
        userNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        userStatusLabel = new JLabel("En ligne");
        userStatusLabel.setForeground(TEXT_MUTED);
        userStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        textPanel.add(userNameLabel); textPanel.add(userStatusLabel);
        infoPanel.add(avatar); infoPanel.add(textPanel);
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controlsPanel.setOpaque(false);
        micBtn = createIconButton("🎤", "Rendre muet");
        micBtn.addActionListener(e -> toggleMic());
        deafenBtn = createIconButton("🎧", "Mettre en sourdine");
        deafenBtn.addActionListener(e -> toggleDeafen());
        settingsBtn = createIconButton("⚙", "Paramètres");
        settingsBtn.addActionListener(e -> openSettingsDialog());
        controlsPanel.add(micBtn); controlsPanel.add(deafenBtn); controlsPanel.add(settingsBtn);
        userBar.add(infoPanel, BorderLayout.CENTER);
        userBar.add(controlsPanel, BorderLayout.EAST);
    }

    private JButton createIconButton(String icon, String tooltip) {
        JButton btn = new JButton(icon);
        btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        btn.setForeground(TEXT_HEADER);
        btn.setBackground(USER_BAR_BG);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(30, 32));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setOpaque(true); btn.setBackground(Color.decode("#3f4147")); }
            public void mouseExited(MouseEvent e) { btn.setOpaque(false); btn.setBackground(USER_BAR_BG); }
        });
        return btn;
    }

    private void joinVoiceChannel(String fullChannelName) {
        if (isVoiceRunning) leaveVoiceChannel();
        try {
            voiceSocket = new DatagramSocket();
            isVoiceRunning = true;
            currentVoiceChannel = fullChannelName;
            InetAddress srvAddr = socket.getInetAddress();
            String displayChan = fullChannelName.contains("|") ? fullChannelName.split("\\|")[1] : fullChannelName;
            voiceChannelLabel.setText(displayChan);
            voicePanel.setVisible(true);
            sidebarBottomContainer.revalidate();
            byte[] header = (fullChannelName + "\0").getBytes();
            DatagramPacket init = new DatagramPacket(header, header.length, srvAddr, 1235);
            voiceSocket.send(init);
            new Thread(new AudioCapture(srvAddr)).start();
            new Thread(new AudioPlayback()).start();
            keepAliveTimer = new Timer();
            keepAliveTimer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    if (isVoiceRunning && voiceSocket != null && !voiceSocket.isClosed()) {
                        try { voiceSocket.send(init); } catch (IOException e) {}
                    }
                }
            }, 1000, 3000);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void leaveVoiceChannel() {
        isVoiceRunning = false;
        if (keepAliveTimer != null) keepAliveTimer.cancel();
        if (voiceSocket != null && !voiceSocket.isClosed()) voiceSocket.close();
        currentVoiceChannel = null;
        voicePanel.setVisible(false);
        sidebarBottomContainer.revalidate();
    }

    private void toggleMic() {
        isMicMuted = !isMicMuted;
        micBtn.setForeground(isMicMuted ? RED_ERROR : TEXT_HEADER);
        micBtn.setText(isMicMuted ? "🚫" : "🎤");
    }

    private void toggleDeafen() {
        isDeafened = !isDeafened;
        deafenBtn.setForeground(isDeafened ? RED_ERROR : TEXT_HEADER);
        deafenBtn.setText(isDeafened ? "🔇" : "🎧");
        if (isDeafened && !isMicMuted) toggleMic();
    }

    private void openSettingsDialog() {
        JDialog settingsDialog = new JDialog(this, "Paramètres Utilisateur", true);
        settingsDialog.setSize(400, 300);
        settingsDialog.setLocationRelativeTo(this);
        settingsDialog.getContentPane().setBackground(DISCORD_BG);
        settingsDialog.setLayout(new GridLayout(4, 1, 10, 10));
        ((JPanel)settingsDialog.getContentPane()).setBorder(new EmptyBorder(20, 20, 20, 20));
        JLabel title = new JLabel("Paramètres Audio & Apparence");
        title.setForeground(TEXT_HEADER); title.setFont(FONT_TITLE); title.setHorizontalAlignment(SwingConstants.CENTER);
        settingsDialog.add(title);
        JPanel volPanel = new JPanel(new BorderLayout()); volPanel.setBackground(DISCORD_BG);
        JLabel volLabel = new JLabel("Volume de Sortie : " + (int)(globalVolume * 100) + "%");
        volLabel.setForeground(TEXT_HEADER);
        JSlider volSlider = new JSlider(0, 100, (int)(globalVolume * 100));
        volSlider.setBackground(DISCORD_BG);
        volSlider.addChangeListener(e -> {
            globalVolume = volSlider.getValue() / 100.0f;
            volLabel.setText("Volume de Sortie : " + volSlider.getValue() + "%");
            updateVolumeRealtime();
        });
        volPanel.add(volLabel, BorderLayout.NORTH); volPanel.add(volSlider, BorderLayout.CENTER);
        settingsDialog.add(volPanel);
        JPanel themePanel = new JPanel(new FlowLayout()); themePanel.setBackground(DISCORD_BG);
        JLabel themeTxt = new JLabel("Thème : "); themeTxt.setForeground(TEXT_HEADER);
        JButton darkBtn = new JButton("Sombre"); JButton lightBtn = new JButton("Clair");
        darkBtn.addActionListener(e -> applyTheme(true)); lightBtn.addActionListener(e -> applyTheme(false));
        themePanel.add(themeTxt); themePanel.add(darkBtn); themePanel.add(lightBtn);
        settingsDialog.add(themePanel);
        JButton closeBtn = new JButton("Terminé"); closeBtn.setBackground(BLURPLE); closeBtn.setForeground(Color.WHITE);
        closeBtn.addActionListener(e -> settingsDialog.dispose());
        settingsDialog.add(closeBtn);
        settingsDialog.setVisible(true);
    }

    private void updateVolumeRealtime() {
        if (currentSpeakers != null && currentSpeakers.isOpen()) {
            try {
                FloatControl volControl = (FloatControl) currentSpeakers.getControl(FloatControl.Type.MASTER_GAIN);
                float vol = (globalVolume < 0.01f) ? 0.0001f : globalVolume;
                float dB = 20.0f * (float) Math.log10(vol);
                dB = Math.max(volControl.getMinimum(), Math.min(volControl.getMaximum(), dB));
                volControl.setValue(dB);
            } catch (Exception ex) {}
        }
    }

    private void applyTheme(boolean isDark) {
        if (isDark) {
            DISCORD_BG = Color.decode("#313338"); DISCORD_SIDEBAR = Color.decode("#2b2d31");
            DISCORD_SERVER_STRIP = Color.decode("#1e1f22"); DISCORD_INPUT_BG = Color.decode("#383a40");
            TEXT_HEADER = Color.decode("#f2f3f5"); TEXT_MUTED = Color.decode("#949ba4");
            USER_BAR_BG = Color.decode("#232428"); VOICE_PANEL_BG = Color.decode("#232428");
        } else {
            DISCORD_BG = Color.decode("#FFFFFF"); DISCORD_SIDEBAR = Color.decode("#F2F3F5");
            DISCORD_SERVER_STRIP = Color.decode("#E3E5E8"); DISCORD_INPUT_BG = Color.decode("#EBEDEF");
            TEXT_HEADER = Color.decode("#060607"); TEXT_MUTED = Color.decode("#4F5660");
            USER_BAR_BG = Color.decode("#EBEDEF"); VOICE_PANEL_BG = Color.decode("#D4D7DC");
        }
        getContentPane().setBackground(DISCORD_BG);
        serverStrip.setBackground(DISCORD_SERVER_STRIP);
        sidebarPanel.setBackground(DISCORD_SIDEBAR); sidebarHeader.setBackground(DISCORD_SIDEBAR);
        sidebarTitle.setBackground(DISCORD_SIDEBAR); sidebarTitle.setForeground(TEXT_HEADER);
        sidebarList.setBackground(DISCORD_SIDEBAR); sidebarList.setForeground(TEXT_MUTED);
        sidebarBottomContainer.setBackground(DISCORD_SIDEBAR);

        chatPanel.setBackground(DISCORD_BG); chatHeader.setBackground(DISCORD_BG);
        chatTitleLabel.setForeground(TEXT_HEADER);
        chatArea.setBackground(DISCORD_BG);
        updateChatDisplay();

        inputContainer.setBackground(DISCORD_BG);
        inputField.setBackground(DISCORD_INPUT_BG); inputField.setForeground(TEXT_HEADER); inputField.setCaretColor(TEXT_HEADER);
        membersPanel.setBackground(DISCORD_SIDEBAR); userList.setBackground(DISCORD_SIDEBAR);
        userBar.setBackground(USER_BAR_BG); userNameLabel.setForeground(TEXT_HEADER);
        micBtn.setBackground(USER_BAR_BG); micBtn.setForeground(isMicMuted ? RED_ERROR : TEXT_HEADER);
        deafenBtn.setBackground(USER_BAR_BG); deafenBtn.setForeground(isDeafened ? RED_ERROR : TEXT_HEADER);
        settingsBtn.setBackground(USER_BAR_BG); settingsBtn.setForeground(TEXT_HEADER);
        voicePanel.setBackground(VOICE_PANEL_BG);
        SwingUtilities.updateComponentTreeUI(this);
    }

    private void handleServerMenu() {
        if (!activeServer.equals("HOME")) {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem inviteItem = new JMenuItem("Inviter des gens");
            inviteItem.addActionListener(x -> out.println("/create_invite " + activeServer));
            JMenuItem createChanItem = new JMenuItem("Créer un salon");
            createChanItem.addActionListener(x -> handleAddAction());
            JMenuItem leaveItem = new JMenuItem("Quitter le serveur");
            leaveItem.setForeground(Color.RED);
            leaveItem.addActionListener(x -> {
                if (JOptionPane.showConfirmDialog(ClientGUI.this, "Quitter " + activeServer + " ?", "Confirmer", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    out.println("/leave " + activeServer);
                }
            });
            menu.add(inviteItem); menu.add(createChanItem); menu.addSeparator(); menu.add(leaveItem);
            menu.show(sidebarTitle, 0, sidebarTitle.getHeight());
        }
    }

    private void handleSidebarClick() {
        String selected = sidebarList.getSelectedValue();
        if (selected == null) return;
        if (activeServer.equals("HOME")) {
            currentContext = "MP:" + selected;
            chatTitleLabel.setText(selected); updateChatDisplay();
        } else {
            if (selected.startsWith("🔊")) {
                joinVoiceChannel(activeServer + "|" + selected);
                chatTitleLabel.setText(selected);
            } else {
                currentContext = activeServer + "|" + selected;
                chatTitleLabel.setText(selected); updateChatDisplay();
            }
        }
    }

    private void createServerDialog() {
        Object[] options = {"Créer un serveur", "Rejoindre un serveur"};
        int n = JOptionPane.showOptionDialog(this, "Que voulez-vous faire ?", "Gestion Serveur", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (n == 0) {
            String name = JOptionPane.showInputDialog(this, "Nom du nouveau serveur (Unique) :");
            if (name != null && !name.trim().isEmpty()) out.println("/create_server " + name.trim());
        } else if (n == 1) {
            String code = JOptionPane.showInputDialog(this, "Code d'invitation :");
            if (code != null) out.println("/join " + code.trim());
        }
    }

    private void handleAddAction() {
        if (activeServer.equals("HOME")) {
            String target = JOptionPane.showInputDialog(this, "Pseudo de l'ami à ajouter :");
            if (target != null) out.println("/friend add " + target.trim());
        } else {
            String[] options = { "Salon Textuel (#)", "Salon Vocal (🔊)" };
            int choice = JOptionPane.showOptionDialog(this, "Type de salon ?", "Créer", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            String prefix = (choice == 1) ? "🔊" : "#";
            String chan = JOptionPane.showInputDialog(this, "Nom du salon :");
            if (chan != null) out.println("/create_channel " + activeServer + " " + (prefix.equals("🔊")?"🔊":"") + chan.trim());
        }
    }

    private void showHomeView() {
        activeServer = "HOME"; currentContext = "HOME"; sidebarTitle.setText("MESSAGES PRIVÉS");
        sidebarList.setModel(friendListModel); chatTitleLabel.setText("Accueil");
        if (serverSettingsBtn != null) serverSettingsBtn.setVisible(false);
        updateUserList(); updateChatDisplay();
    }

    private void showServerView(String serverName) {
        activeServer = serverName; sidebarTitle.setText(serverName.toUpperCase());
        channelListModel.clear();
        List<String> chans = serverChannels.getOrDefault(serverName, new ArrayList<>());
        for (String c : chans) channelListModel.addElement(c);
        sidebarList.setModel(channelListModel);
        if (!chans.isEmpty()) { currentContext = serverName + "|" + chans.get(0); chatTitleLabel.setText(chans.get(0)); }
        if (serverSettingsBtn != null) serverSettingsBtn.setVisible(true);
        updateUserList(); updateChatDisplay();
    }

    private void updateUserList() {
        userListModel.clear();
        if (activeServer.equals("HOME")) for (String u : globalOnlineUsers) userListModel.addElement(u);
        else for (String m : serverMembers.getOrDefault(activeServer, new ArrayList<>())) userListModel.addElement(m);
    }

    private JPanel createRoundButton(Color color, String text, String tooltip) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(color); p.setPreferredSize(new Dimension(48, 48));
        p.setCursor(new Cursor(Cursor.HAND_CURSOR)); p.setToolTipText(tooltip);
        JLabel l = new JLabel(text, SwingConstants.CENTER); l.setForeground(Color.WHITE); l.setFont(new Font("Segoe UI", Font.BOLD, 20));
        p.add(l, BorderLayout.CENTER); p.setBorder(new EmptyBorder(0, 0, 0, 0));
        return p;
    }

    private void addServerIcon(String name) {
        for (Component c : serverStrip.getComponents()) {
            if (c instanceof JPanel && name.equals(((JPanel)c).getToolTipText())) return;
        }
        int hash = name.hashCode();
        Color c = new Color((hash & 0xFF0000) >> 16, (hash & 0x00FF00) >> 8, hash & 0x0000FF).brighter();
        JPanel srvBtn = createRoundButton(c, name.substring(0, 1).toUpperCase(), name);
        srvBtn.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { showServerView(name); } });
        serverStrip.add(srvBtn, serverStrip.getComponentCount() - 1);
        serverStrip.revalidate(); serverStrip.repaint();
    }

    private void openRoleManager() {
        if (activeServer.equals("HOME")) return;

        JDialog roleDialog = new JDialog(this, "Paramètres du Serveur - " + activeServer, true);
        roleDialog.setSize(700, 500);
        roleDialog.setLocationRelativeTo(this);
        roleDialog.getContentPane().setBackground(DISCORD_BG);
        roleDialog.setLayout(new BorderLayout());

        JPanel leftPanel = new JPanel();
        leftPanel.setPreferredSize(new Dimension(200, 0));
        leftPanel.setBackground(DISCORD_SIDEBAR);
        leftPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        JLabel title = new JLabel("RÔLES");
        title.setForeground(TEXT_MUTED);
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setBorder(new EmptyBorder(10, 10, 10, 10));
        leftPanel.add(title);
        JLabel tabRole = new JLabel("Gestion des Rôles");
        tabRole.setForeground(Color.WHITE);
        tabRole.setFont(FONT_BOLD);
        tabRole.setBorder(new EmptyBorder(5, 10, 5, 10));
        tabRole.setOpaque(true);
        tabRole.setBackground(Color.decode("#3f4147"));
        leftPanel.add(tabRole);
        roleDialog.add(leftPanel, BorderLayout.WEST);

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(DISCORD_BG);
        contentPanel.setBorder(new EmptyBorder(20, 40, 20, 40));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(DISCORD_BG);
        JLabel pageTitle = new JLabel("Rôles du serveur");
        pageTitle.setForeground(TEXT_HEADER);
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
        JButton createBtn = new JButton("Création de rôle");
        createBtn.setBackground(BLURPLE);
        createBtn.setForeground(Color.WHITE);
        createBtn.setFocusPainted(false);
        header.add(pageTitle, BorderLayout.WEST);
        header.add(createBtn, BorderLayout.EAST);
        contentPanel.add(header, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridLayout(2, 1, 0, 20));
        formPanel.setBackground(DISCORD_BG);
        formPanel.setBorder(new EmptyBorder(20, 0, 0, 0));

        JPanel createPanel = new JPanel(new BorderLayout());
        createPanel.setBackground(DISCORD_INPUT_BG);
        createPanel.setBorder(BorderFactory.createTitledBorder(null, "NOUVEAU RÔLE", 0, 0, new Font("Segoe UI", Font.BOLD, 10), TEXT_MUTED));
        JPanel inputs = new JPanel(new GridLayout(5, 1, 5, 5));
        inputs.setBackground(DISCORD_INPUT_BG);
        inputs.setBorder(new EmptyBorder(10, 10, 10, 10));
        JTextField roleNameField = new JTextField();
        roleNameField.setBorder(BorderFactory.createTitledBorder("Nom du rôle"));
        JCheckBox pAdmin = new JCheckBox("Administrateur (Tout faire)");
        JCheckBox pBan = new JCheckBox("Bannir des membres");
        JCheckBox pKick = new JCheckBox("Expulser des membres");
        JCheckBox pChan = new JCheckBox("Gérer les salons (Créer/Supprimer)");
        for (JCheckBox cb : new JCheckBox[]{pAdmin, pBan, pKick, pChan}) {
            cb.setBackground(DISCORD_INPUT_BG);
            cb.setForeground(TEXT_HEADER);
            cb.setFocusPainted(false);
        }
        inputs.add(roleNameField);
        inputs.add(pAdmin); inputs.add(pBan); inputs.add(pKick); inputs.add(pChan);
        createPanel.add(inputs, BorderLayout.CENTER);

        createBtn.addActionListener(e -> {
            String rName = roleNameField.getText().trim();
            if (rName.isEmpty()) return;
            int perms = 0;
            if (pAdmin.isSelected()) perms |= PERM_ADMIN;
            if (pBan.isSelected()) perms |= PERM_BAN;
            if (pKick.isSelected()) perms |= PERM_KICK;
            if (pChan.isSelected()) perms |= PERM_CHANNELS;
            out.println("/role create " + activeServer + " " + rName + " " + perms);
            JOptionPane.showMessageDialog(roleDialog, "Commande envoyée : Création du rôle " + rName);
            roleNameField.setText("");
        });

        JPanel assignPanel = new JPanel(new BorderLayout());
        assignPanel.setBackground(DISCORD_INPUT_BG);
        assignPanel.setBorder(BorderFactory.createTitledBorder(null, "ATTRIBUER UN RÔLE", 0, 0, new Font("Segoe UI", Font.BOLD, 10), TEXT_MUTED));
        JPanel assignInputs = new JPanel(new GridLayout(3, 1, 5, 5));
        assignInputs.setBackground(DISCORD_INPUT_BG);
        assignInputs.setBorder(new EmptyBorder(10, 10, 10, 10));
        JTextField targetUserField = new JTextField();
        targetUserField.setBorder(BorderFactory.createTitledBorder("Pseudo du membre"));
        JTextField targetRoleField = new JTextField();
        targetRoleField.setBorder(BorderFactory.createTitledBorder("Nom du rôle à donner"));
        JButton assignBtn = new JButton("Donner le rôle");
        assignBtn.setBackground(ONLINE_GREEN);
        assignBtn.setForeground(Color.WHITE);
        assignInputs.add(targetUserField);
        assignInputs.add(targetRoleField);
        assignInputs.add(assignBtn);
        assignPanel.add(assignInputs, BorderLayout.CENTER);

        assignBtn.addActionListener(e -> {
            String tUser = targetUserField.getText().trim();
            String tRole = targetRoleField.getText().trim();
            if (!tUser.isEmpty() && !tRole.isEmpty()) {
                out.println("/role add " + activeServer + " " + tUser + " " + tRole);
                JOptionPane.showMessageDialog(roleDialog, "Commande envoyée : " + tRole + " -> " + tUser);
            }
        });

        formPanel.add(createPanel);
        formPanel.add(assignPanel);
        contentPanel.add(formPanel, BorderLayout.CENTER);
        roleDialog.add(contentPanel, BorderLayout.CENTER);
        roleDialog.setVisible(true);
    }

    static class PaddingRenderer extends DefaultListCellRenderer {
        private int padding;
        public PaddingRenderer(int p) { this.padding = p; }
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(new EmptyBorder(padding/2, padding, padding/2, padding));
            if (isSelected) { label.setBackground(Color.decode("#3f4147")); label.setForeground(Color.WHITE); }
            else { label.setBackground(DISCORD_SIDEBAR); label.setForeground(Color.decode("#949ba4")); }
            return label;
        }
    }

    class DiscordMemberRenderer extends JPanel implements ListCellRenderer<String> {
        private JLabel nameLabel;
        private JPanel avatarPanel;
        public DiscordMemberRenderer() {
            setLayout(new BorderLayout(10, 0)); setBackground(DISCORD_SIDEBAR); setBorder(new EmptyBorder(5, 10, 5, 10));
            avatarPanel = new JPanel() {
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g); Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(BLURPLE); g2.fillOval(0, 0, 32, 32);
                    String u = nameLabel.getText(); boolean online = globalOnlineUsers.contains(u);
                    g2.setColor(DISCORD_SIDEBAR); g2.fillOval(22, 22, 14, 14);
                    g2.setColor(online ? ONLINE_GREEN : OFFLINE_GRAY); g2.fillOval(24, 24, 10, 10);
                }
            };
            avatarPanel.setPreferredSize(new Dimension(35, 35)); avatarPanel.setOpaque(false);
            nameLabel = new JLabel(); nameLabel.setFont(FONT_BOLD);
            add(avatarPanel, BorderLayout.WEST); add(nameLabel, BorderLayout.CENTER);
        }
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(value); boolean online = globalOnlineUsers.contains(value);
            if (isSelected) { setBackground(Color.decode("#3f4147")); nameLabel.setForeground(Color.WHITE); }
            else { setBackground(DISCORD_SIDEBAR); nameLabel.setForeground(online ? TEXT_HEADER : TEXT_MUTED); }
            return this;
        }
    }

    private class AudioCapture implements Runnable {
        InetAddress srvAddr;
        public AudioCapture(InetAddress a) { this.srvAddr = a; }
        @Override
        public void run() {
            try {
                AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                if (!AudioSystem.isLineSupported(info)) return;
                TargetDataLine micro = (TargetDataLine) AudioSystem.getLine(info);
                micro.open(format); micro.start();
                byte[] buffer = new byte[1024];
                byte[] header = (currentVoiceChannel + "\0").getBytes();
                while (isVoiceRunning) {
                    if (micro.available() > 0) {
                        int read = micro.read(buffer, 0, buffer.length);
                        if (read > 0 && !isMicMuted) {
                            byte[] packetData = new byte[header.length + read];
                            System.arraycopy(header, 0, packetData, 0, header.length);
                            System.arraycopy(buffer, 0, packetData, header.length, read);
                            DatagramPacket packet = new DatagramPacket(packetData, packetData.length, srvAddr, 1235);
                            if (voiceSocket != null && !voiceSocket.isClosed()) voiceSocket.send(packet);
                        }
                    } else { Thread.sleep(10); }
                }
                micro.close();
            } catch (Exception e) {}
        }
    }

    private class AudioPlayback implements Runnable {
        @Override
        public void run() {
            try {
                AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info);
                speakers.open(format); speakers.start();
                currentSpeakers = speakers;
                updateVolumeRealtime();
                byte[] buffer = new byte[4096];
                while (isVoiceRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    if (voiceSocket != null && !voiceSocket.isClosed()) {
                        voiceSocket.receive(packet);
                        if (isDeafened) continue;
                        byte[] data = packet.getData();
                        int len = packet.getLength();
                        int offset = 0;
                        for (int i = 0; i < len; i++) { if (data[i] == 0) { offset = i + 1; break; } }
                        if (offset < len) speakers.write(data, offset, len - offset);
                    }
                }
                speakers.close();
            } catch (Exception e) {}
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}