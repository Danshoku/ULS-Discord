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

    // --- COULEURS (GLASSMORPHISM PALETTE) ---
    // Background (Dark Gradient base)
    private static Color DISCORD_BG = new Color(20, 20, 25);

    // Text
    private static Color TEXT_HEADER = new Color(255, 255, 255);
    private static Color TEXT_MUTED = new Color(180, 184, 190);

    // Accents
    private static final Color BLURPLE = new Color(88, 101, 242); // Discord Blue
    private static final Color RED_ERROR = new Color(218, 55, 60);

    // Inputs
    private static Color INPUT_BG = new Color(0, 0, 0, 100);
    private static Color DISCORD_INPUT_BG = INPUT_BG;

    // --- PERMISSIONS ---
    public static final int PERM_ADMIN = 1;
    public static final int PERM_BAN = 2;
    public static final int PERM_KICK = 4;
    public static final int PERM_CHANNELS = 8;

    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 16);

    // --- VARIABLES GLOBALES ---
    String username;
    PrintWriter out;
    BufferedReader in;
    Socket socket;

    String activeServer = "HOME";
    String currentContext = "HOME";

    Map<String, List<String>> messageStorage = new HashMap<>();
    Map<String, List<String>> serverChannels = new HashMap<>();
    Map<String, List<String>> serverMembers = new HashMap<>();
    Set<String> globalOnlineUsers = new HashSet<>();

    // UI Components
    // UI Components managed by ModernUI
    ModernUI ui;

    boolean isMicMuted = false;
    boolean isDeafened = false;
    float globalVolume = 1.0f;
    SourceDataLine currentSpeakers = null;

    public DefaultListModel<String> sidebarListModel; // Channels + Voice Users
    public DefaultListModel<String> userListModel; // Members (Right side)
    public Map<String, String> voiceStates = new HashMap<>(); // User -> FullChannelName
    DefaultListModel<String> channelListModel;
    DefaultListModel<String> friendListModel;

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

        sidebarListModel = new DefaultListModel<>();
        friendListModel = new DefaultListModel<>();
        channelListModel = new DefaultListModel<>();
        userListModel = new DefaultListModel<>();

        buildInterface();

        new Thread(new IncomingReader()).start();
    }

    // ====================================================================================
    // --- FENÊTRE DE LOGIN ---
    // ====================================================================================
    public static class LoginFrame extends JFrame {
        private BufferedImage pessiImg;
        private BufferedImage sinjImg;
        private BufferedImage mainsBasImg;
        private BufferedImage logoPiscordImg;

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
                    BorderFactory.createLineBorder(TEXT_MUTED), "Mot de passe", 0, 0,
                    new Font("Segoe UI", Font.BOLD, 12), TEXT_HEADER));

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
                    BorderFactory.createLineBorder(TEXT_MUTED), title, 0, 0, new Font("Segoe UI", Font.BOLD, 12),
                    TEXT_HEADER));
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
                sinjImg = loadImage("sinj.png"); // Image Main Gauche
                mainsBasImg = loadImage("image_7.png");
                imageMainDroite = loadImage("67.png"); // Image Main Droite (67) is now imageMainDroite
                logoPiscordImg = loadImage("image_6.png");
            } catch (Exception e) {
            }
        }

        private BufferedImage loadImage(String name) throws IOException {
            URL url = ClientGUI.class.getResource("/" + name);
            if (url == null)
                url = ClientGUI.class.getResource("/client/" + name);
            if (url != null)
                return ImageIO.read(url);
            File f = new File(name);
            if (f.exists())
                return ImageIO.read(f);
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
                    // Main Gauche : sinj.png
                    if (sinjImg != null)
                        g2d.drawImage(imageMainDroite, leftShoulderX - handSize / 2, shoulderY - handSize / 2, handSize,
                                handSize, null);
                    // Main Droite : 67.png
                    if (imageMainDroite != null)
                        g2d.drawImage(imageMainDroite, rightShoulderX - handSize / 2, shoulderY - handSize / 2,
                                handSize, handSize, null);
                }

                if (mainsBasImg != null) {
                    int mainsW = 400;
                    int mainsH = (int) ((double) mainsBasImg.getHeight() / mainsBasImg.getWidth() * mainsW);
                    g2d.drawImage(mainsBasImg, cx - mainsW / 2, h - mainsH - 20, mainsW, mainsH, null);
                }

                if (pessiImg != null)
                    g2d.drawImage(pessiImg, pessiX, pessiY, 150, 150, null);

                if (sinjImg != null) {
                    double radius = 220;
                    int sx = (int) (cx + Math.cos(sinjAngle) * radius);
                    int sy = (int) (h / 2 + Math.sin(sinjAngle) * radius);
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
            if (ip.isEmpty())
                ip = "127.0.0.1";

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
                        app.showHomeView();

                        // UPDATE USER STATUS IMMEDIATELY
                        SwingUtilities.invokeLater(() -> {
                            app.ui.userNameLabel.setText(app.username);
                            app.ui.userStatusLabel.setText("En ligne");
                            app.ui.userBar.revalidate();
                            app.ui.userBar.repaint();
                        });

                        app.out.println("/get_friends");
                        // Start Reader
                        new Thread(app.new IncomingReader()).start();
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

    public void buildInterface() {
        // Delegate to ModernUI (Glassmorphism)
        ui = new ModernUI(this);
        ui.initUI();
    }

    // --- GESTION IMAGES ---
    boolean pasteImageFromClipboard() {
        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t == null)
                return false;
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void processClipboardImage(Image img) {
        try {
            BufferedImage bi;
            if (img instanceof BufferedImage)
                bi = (BufferedImage) img;
            else {
                bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                Graphics g = bi.createGraphics();
                g.drawImage(img, 0, 0, null);
                g.dispose();
            }
            File tempFile = File.createTempFile("paste_clip", ".png");
            ImageIO.write(bi, "png", tempFile);
            processAndSendImage(tempFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void sendImage() {
        if (currentContext.equals("HOME")) {
            JOptionPane.showMessageDialog(this, "Sélectionnez d'abord un salon ou un ami.");
            return;
        }
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Envoyer une image");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Images (JPG, PNG, GIF)", "jpg", "png", "gif", "jpeg"));
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            processAndSendImage(fileChooser.getSelectedFile());
        }
    }

    private void processAndSendImage(File file) {
        if (file.length() > MAX_IMG_SIZE) {
            JOptionPane.showMessageDialog(this, "L'image est trop lourde (>5Mo).");
            return;
        }
        try {
            byte[] fileContent = java.nio.file.Files.readAllBytes(file.toPath());
            String encoded = Base64.getEncoder().encodeToString(fileContent);
            encoded = encoded.replaceAll("\\s", "");
            sendBase64Image(encoded);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendBase64Image(String b64) {
        String protocolMsg = "IMG_B64:" + b64;
        out.println(currentContext + "///" + protocolMsg);
    }

    void sendMessage() {
        String msg = ui.inputField.getText().trim();
        if (!msg.isEmpty() && out != null) {
            if (currentContext.equals("HOME")) {
                JOptionPane.showMessageDialog(this, "Sélectionnez un ami ou un salon !");
                return;
            }
            if (msg.startsWith("data:image")) {
                try {
                    String base64 = msg.substring(msg.indexOf(",") + 1);
                    sendBase64Image(base64);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Format d'image invalide.");
                }
            } else {
                out.println(currentContext + "///" + msg);
            }
            ui.inputField.setText("");
        }
    }

    private void updateSidebar() {
        sidebarListModel.clear();

        List<String> chans = serverChannels.get(activeServer);
        if (chans != null) {
            for (String chan : chans) {
                sidebarListModel.addElement(chan); // Channel Name (starts with # or 🔊)

                String fullChan = activeServer + "|" + chan;

                for (Map.Entry<String, String> entry : voiceStates.entrySet()) {
                    if (entry.getValue().equals(fullChan)) {
                        sidebarListModel.addElement("  " + entry.getKey());
                    }
                }
            }
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
                            html.append("<br><img src='").append(path)
                                    .append("' style='max-width: 300px; height: auto;'><br>");
                        } else {
                            html.append("[Image Corrompue]");
                        }
                    } catch (Exception e) {
                        html.append("[Erreur Image]");
                    }
                } else {
                    String processed = processTextForLinks(content);
                    html.append("<span style='color:#dbdee1;'>").append(processed).append("</span>");
                }
                html.append("</div>");
            }
        }
        html.append("</body></html>");
        ui.chatArea.setText(html.toString());
        SwingUtilities.invokeLater(() -> ui.chatArea.setCaretPosition(ui.chatArea.getDocument().getLength()));
    }

    private Map<Integer, File> imageCache = new HashMap<>();

    private File writeBase64ToTempFile(String b64) {
        int hash = b64.hashCode();
        if (imageCache.containsKey(hash))
            return imageCache.get(hash);
        try {
            byte[] data = Base64.getDecoder().decode(b64);
            File temp = File.createTempFile("piscord_img_" + hash, ".png");
            temp.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(temp)) {
                fos.write(data);
            }
            imageCache.put(hash, temp);
            return temp;
        } catch (Exception e) {
            return null;
        }
    }

    private String processTextForLinks(String text) {
        String urlRegex = "(https?://\\S+\\.(png|jpg|jpeg|gif))";
        String simpleUrlRegex = "(https?://\\S+)";
        Pattern imgPat = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE);
        Matcher imgMat = imgPat.matcher(text);
        if (imgMat.find()) {
            return imgMat.replaceAll("<br><img src='$1' style='max-width: 200px; height: auto;'><br>");
        }
        Pattern urlPat = Pattern.compile(simpleUrlRegex, Pattern.CASE_INSENSITIVE);
        Matcher urlMat = urlPat.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (urlMat.find()) {
            urlMat.appendReplacement(sb, "<a href='$1'>$1</a>");
        }
        urlMat.appendTail(sb);
        return sb.toString();
    }

    private class IncomingReader implements Runnable {
        public void run() {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.startsWith("NEW_SERVER:")) {
                        String finalMsg = msg;
                        SwingUtilities.invokeLater(() -> addServerIcon(finalMsg.substring(11)));
                    } else if (msg.startsWith("NEW_CHANNEL:")) {
                        String[] p = msg.substring(12).split("\\|");
                        serverChannels.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p[1]);
                        if (activeServer.equals(p[0]))
                            SwingUtilities.invokeLater(ClientGUI.this::updateSidebar);
                    } else if (msg.startsWith("CHANNEL_DELETED:")) {
                        String[] parts = msg.substring(16).trim().split("\\|");
                        if (parts.length == 2) {
                            String srv = parts[0];
                            String chan = parts[1];
                            List<String> chans = serverChannels.getOrDefault(srv, new ArrayList<>());
                            chans.remove(chan);
                            if (activeServer.equals(srv)) {
                                updateSidebar();
                                if (currentContext.equals(srv + "|" + chan)) {
                                    // Switched to general or clear
                                    if (!chans.isEmpty()) {
                                        currentContext = srv + "|" + chans.get(0);
                                        ui.chatTitleLabel.setText(chans.get(0));
                                        updateChatDisplay();
                                    } else {
                                        ui.chatTitleLabel.setText("");
                                        ui.chatArea.setText("");
                                    }
                                }
                            }
                        }
                    } else if (msg.startsWith("CHANNEL_RENAMED:")) {
                        // CHANNEL_RENAMED:Server|OldName|NewName
                        String[] parts = msg.substring(16).trim().split("\\|");
                        if (parts.length == 3) {
                            String srv = parts[0];
                            String oldName = parts[1];
                            String newName = parts[2];
                            List<String> chans = serverChannels.getOrDefault(srv, new ArrayList<>());
                            int idx = chans.indexOf(oldName);
                            if (idx != -1) {
                                chans.set(idx, newName);
                                if (activeServer.equals(srv)) {
                                    updateSidebar();
                                    if (currentContext.equals(srv + "|" + oldName)) {
                                        currentContext = srv + "|" + newName;
                                        ui.chatTitleLabel.setText(newName);
                                    }
                                }
                            }
                        }
                    } else if (msg.startsWith("VOICE_JOIN:")) {
                        String[] p = msg.substring(12).split(":");
                        if (p.length == 2) {
                            voiceStates.put(p[0], p[1]);
                            if (activeServer.equals(p[1].split("\\|")[0]))
                                SwingUtilities.invokeLater(ClientGUI.this::updateSidebar);
                        }
                    } else if (msg.startsWith("VOICE_JOIN:")) {
                        String[] p = msg.substring(11).split(":"); // user:channel
                        if (p.length == 2) {
                            voiceStates.put(p[0], p[1]);
                            if (activeServer.equals(p[1].split("\\|")[0]))
                                SwingUtilities.invokeLater(ClientGUI.this::updateSidebar);
                        }
                    } else if (msg.startsWith("VOICE_LEAVE:")) {
                        String u = msg.substring(12);
                        String oldChan = voiceStates.remove(u);
                        if (oldChan != null && activeServer.equals(oldChan.split("\\|")[0]))
                            SwingUtilities.invokeLater(ClientGUI.this::updateSidebar);
                    } else if (msg.startsWith("MSG:") || msg.startsWith("HISTORY:")) {
                        String payload = msg.contains("MSG:") ? msg.substring(4) : msg.substring(8);
                        String[] parts = payload.split("///");
                        if (parts.length >= 4) {
                            String ctx = parts[0], snd = parts[1], cnt = parts[2], time = parts[3];
                            messageStorage.computeIfAbsent(ctx, k -> new ArrayList<>())
                                    .add("[" + time + "] " + snd + ": " + cnt);
                            if (currentContext.equals(ctx)
                                    || (currentContext.startsWith("MP:") && ctx.contains(username)))
                                SwingUtilities.invokeLater(() -> updateChatDisplay());
                        }
                    } else if (msg.startsWith("FRIENDLIST:")) {
                        String raw = msg.substring(11);
                        if (!raw.isEmpty())
                            SwingUtilities.invokeLater(() -> {
                                friendListModel.clear();
                                for (String f : raw.split(","))
                                    friendListModel.addElement(f);
                            });
                    } else if (msg.startsWith("USERLIST:")) {
                        String[] users = msg.equals("USERLIST:") ? new String[0] : msg.substring(9).split(",");
                        globalOnlineUsers.clear();
                        Collections.addAll(globalOnlineUsers, users);
                        SwingUtilities.invokeLater(() -> updateUserList());
                    } else if (msg.startsWith("SERVER_MEMBERS:")) {
                        String[] p = msg.substring(15).split("\\|");
                        if (p.length == 2) {
                            serverMembers.put(p[0], Arrays.asList(p[1].split(",")));
                            if (activeServer.equals(p[0]))
                                SwingUtilities.invokeLater(() -> updateUserList());
                        }
                    }
                }
            } catch (IOException e) {
            }
        }
    }

    // buildVoicePanel moved to ModernUI
    // buildUserBar moved to ModernUI

    private void joinVoiceChannel(String fullChannelName) {
        if (isVoiceRunning)
            leaveVoiceChannel();
        try {
            out.println("VOICE_JOIN " + fullChannelName);
            voiceSocket = new DatagramSocket();
            isVoiceRunning = true;
            currentVoiceChannel = fullChannelName;
            InetAddress srvAddr = socket.getInetAddress();
            String displayChan = fullChannelName.contains("|") ? fullChannelName.split("\\|")[1] : fullChannelName;
            ui.voiceChannelLabel.setText(displayChan);
            ui.voicePanel.setVisible(true);
            ui.sidebarBottomContainer.revalidate();
            // 1. Envoi paquet d'initialisation pour s'enregistrer au serveur (V2: Header +
            // \0)
            byte[] header = (fullChannelName + "\0").getBytes();
            DatagramPacket init = new DatagramPacket(header, header.length, srvAddr, 1235);
            voiceSocket.send(init);

            // 2. Lancement Capture et Lecture (16kHz)
            new Thread(new AudioCapture(srvAddr)).start();
            new Thread(new AudioPlayback()).start();

            // 3. Keep-Alive
            keepAliveTimer = new Timer();
            keepAliveTimer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    if (isVoiceRunning && voiceSocket != null && !voiceSocket.isClosed()) {
                        try {
                            voiceSocket.send(init);
                        } catch (IOException e) {
                        }
                    }
                }
            }, 1000, 3000); // 3s heartbeat

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Erreur Vocal: " + e.getMessage());
        }
    }

    void leaveVoiceChannel() {
        if (isVoiceRunning)
            out.println("VOICE_LEAVE");
        isVoiceRunning = false;
        if (keepAliveTimer != null)
            keepAliveTimer.cancel();
        if (voiceSocket != null && !voiceSocket.isClosed())
            voiceSocket.close();
        currentVoiceChannel = null;
        ui.voicePanel.setVisible(false);
        ui.sidebarBottomContainer.revalidate();
    }

    void toggleMic() {
        isMicMuted = !isMicMuted;
        ui.micBtn.setForeground(isMicMuted ? RED_ERROR : TEXT_HEADER);
        ui.micBtn.setText(isMicMuted ? "🚫" : "🎤");
        repaint();
    }

    void toggleDeafen() {
        isDeafened = !isDeafened;
        ui.deafenBtn.setForeground(isDeafened ? RED_ERROR : TEXT_HEADER);
        ui.deafenBtn.setText(isDeafened ? "🔇" : "🎧");
        if (isDeafened && !isMicMuted)
            toggleMic();
        repaint();
    }

    void openSettingsDialog() {
        JDialog settingsDialog = new JDialog(this, "Paramètres Utilisateur", true);
        settingsDialog.setSize(400, 300);
        settingsDialog.setLocationRelativeTo(this);
        settingsDialog.getContentPane().setBackground(DISCORD_BG);
        settingsDialog.setLayout(new GridLayout(4, 1, 10, 10));
        ((JPanel) settingsDialog.getContentPane()).setBorder(new EmptyBorder(20, 20, 20, 20));
        JLabel title = new JLabel("Paramètres Audio & Apparence");
        title.setForeground(TEXT_HEADER);
        title.setFont(FONT_TITLE);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        settingsDialog.add(title);
        JPanel volPanel = new JPanel(new BorderLayout());
        volPanel.setBackground(DISCORD_BG);
        JLabel volLabel = new JLabel("Volume de Sortie : " + (int) (globalVolume * 100) + "%");
        volLabel.setForeground(TEXT_HEADER);
        JSlider volSlider = new JSlider(0, 100, (int) (globalVolume * 100));
        volSlider.setBackground(DISCORD_BG);
        volSlider.addChangeListener(e -> {
            globalVolume = volSlider.getValue() / 100.0f;
            volLabel.setText("Volume de Sortie : " + volSlider.getValue() + "%");
            updateVolumeRealtime();
        });
        volPanel.add(volLabel, BorderLayout.NORTH);
        volPanel.add(volSlider, BorderLayout.CENTER);
        settingsDialog.add(volPanel);
        JPanel themePanel = new JPanel(new FlowLayout());
        themePanel.setBackground(DISCORD_BG);
        JLabel themeTxt = new JLabel("Thème : ");
        themeTxt.setForeground(TEXT_HEADER);
        JButton darkBtn = new JButton("Sombre");
        JButton lightBtn = new JButton("Clair");
        darkBtn.addActionListener(e -> applyTheme(true));
        lightBtn.addActionListener(e -> applyTheme(false));
        themePanel.add(themeTxt);
        themePanel.add(darkBtn);
        themePanel.add(lightBtn);
        settingsDialog.add(themePanel);
        JButton closeBtn = new JButton("Terminé");
        closeBtn.setBackground(BLURPLE);
        closeBtn.setForeground(Color.WHITE);
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
            } catch (Exception ex) {
            }
        }
    }

    private void applyTheme(boolean isDark) {
        if (isDark) {
            DISCORD_BG = new Color(54, 57, 63);
        } else {
            DISCORD_BG = Color.decode("#FFFFFF");
        }
        getContentPane().setBackground(DISCORD_BG);
        ui.applyTheme(isDark);
    }

    void handleSidebarClick() {
        String selected = ui.sidebarList.getSelectedValue();
        if (selected == null)
            return;
        if (activeServer.equals("HOME")) {
            currentContext = "MP:" + selected;
            ui.chatTitleLabel.setText(selected);
            updateChatDisplay();
        } else {
            if (selected.startsWith("🔊")) {
                joinVoiceChannel(activeServer + "|" + selected);
                ui.chatTitleLabel.setText(selected);
            } else {
                currentContext = activeServer + "|" + selected;
                ui.chatTitleLabel.setText(selected);
                updateChatDisplay();
            }
        }
    }

    void createServerDialog() {
        ui.createServerDialog();
    }

    void launchGame() {
        new Thread(() -> {
            try {
                System.out.println("Lancement de PacMan...");
                File jarFile = new File("PacManClient.jar");
                if (!jarFile.exists()) {
                    JOptionPane.showMessageDialog(this, "Fichier PacManClient.jar introuvable !");
                    return;
                }
                ProcessBuilder pb = new ProcessBuilder("java", "-jar", "PacManClient.jar");
                pb.inheritIO();
                pb.start();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Erreur lancement jeu : " + e.getMessage());
            }
        }).start();
    }

    void handleAddAction() {
        if (activeServer.equals("HOME")) {
            JOptionPane.showMessageDialog(this, "Impossible créer salon dans accueil.");
            return;
        }

        // Create Text Channel Only
        String name = JOptionPane.showInputDialog(this, "Nom du salon textuel :");
        if (name != null && !name.trim().isEmpty()) {
            // Force prefix #
            String cleanName = name.trim().replaceAll("^[#\\s]+", "");
            out.println("create_channel " + activeServer + " #" + cleanName);
        }
    }

    void deleteChannel(String channelName) {
        if (activeServer.equals("HOME"))
            return;
        int conf = JOptionPane.showConfirmDialog(this, "Voulez-vous vraiment supprimer " + channelName + " ?",
                "Suppression", JOptionPane.YES_NO_OPTION);
        if (conf == JOptionPane.YES_OPTION) {
            out.println("DELETE_CHANNEL " + activeServer + "|" + channelName);
        }
    }

    void renameChannel(String channelName) {
        if (activeServer.equals("HOME"))
            return;
        String newName = JOptionPane.showInputDialog(this, "Nouveau nom pour " + channelName + " :", channelName);
        if (newName != null && !newName.trim().isEmpty() && !newName.equals(channelName)) {
            // Keep prefix if present?
            // Logic: If old was #gen, new input "foo" -> #foo
            // If old was 🔊 gen, new input "bar" -> 🔊 bar
            String prefix = "";
            if (channelName.startsWith("#"))
                prefix = "#";
            else if (channelName.startsWith("🔊 "))
                prefix = "🔊 ";

            // Clean input to remove prefix if user typed it
            String cleanNew = newName.trim().replaceAll("^[#🔊\\s]+", "");

            out.println("RENAME_CHANNEL " + activeServer + "|" + channelName + "|" + prefix + cleanNew);
        }
    }

    public void handleServerMenu() {
        if (activeServer.equals("HOME"))
            return;

        JPopupMenu menu = new JPopupMenu();

        JMenuItem create = new JMenuItem("Créer un salon");
        create.addActionListener(e -> handleAddAction());
        menu.add(create);

        JMenuItem invite = new JMenuItem("Inviter des gens");
        invite.addActionListener(e -> {
            String code = JOptionPane.showInputDialog(this, "Création d'invitation..."); // Simplify for now or call
                                                                                         // logic
            out.println("/create_invite " + activeServer);
        });
        menu.add(invite);

        JMenuItem settings = new JMenuItem("Paramètres du serveur");
        settings.addActionListener(e -> openRoleManager());
        menu.add(settings);

        menu.addSeparator();

        JMenuItem leave = new JMenuItem("Quitter le serveur");
        leave.setForeground(Color.RED);
        leave.addActionListener(e -> {
            int conf = JOptionPane.showConfirmDialog(this, "Voulez-vous quitter " + activeServer + " ?", "Quitter",
                    JOptionPane.YES_NO_OPTION);
            if (conf == JOptionPane.YES_OPTION)
                out.println("/leave " + activeServer);
        });
        menu.add(leave);

        // Show below the title
        menu.show(ui.sidebarTitle, 0, ui.sidebarTitle.getHeight());
    }

    void showHomeView() {
        activeServer = "HOME";
        currentContext = "HOME";
        ui.sidebarTitle.setText("MESSAGES PRIVÉS");
        ui.sidebarList.setModel(friendListModel);
        ui.chatTitleLabel.setText("Accueil");
        if (ui.serverSettingsBtn != null)
            ui.serverSettingsBtn.setVisible(false);
        updateUserList();
        updateChatDisplay();
    }

    void showServerView(String serverName) {
        activeServer = serverName;
        ui.sidebarTitle.setText(serverName.toUpperCase());

        ui.sidebarList.setModel(sidebarListModel);
        updateSidebar();

        List<String> chans = serverChannels.getOrDefault(serverName, new ArrayList<>());
        if (!chans.isEmpty()) {
            currentContext = serverName + "|" + chans.get(0);
            ui.chatTitleLabel.setText(chans.get(0));
        }
        if (ui.serverSettingsBtn != null)
            ui.serverSettingsBtn.setVisible(true);
        updateUserList();
        updateChatDisplay();
    }

    void updateUserList() {
        userListModel.clear();
        if (activeServer.equals("HOME"))
            for (String u : globalOnlineUsers)
                userListModel.addElement(u);
        else
            for (String m : serverMembers.getOrDefault(activeServer, new ArrayList<>()))
                userListModel.addElement(m);
    }

    void addServerIcon(String name) {
        ui.addServerIcon(name);
    }

    void openRoleManager() {
        ui.openRoleManager();
    }

    private class AudioCapture implements Runnable {
        InetAddress srvAddr;

        public AudioCapture(InetAddress a) {
            this.srvAddr = a;
        }

        @Override
        public void run() {
            try {
                // V2: 16kHz, 16bit, Mono, Signed, Little Endian (false)
                AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

                if (!AudioSystem.isLineSupported(info)) {
                    System.err.println("Micro non supporté");
                    return;
                }

                TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(format);
                microphone.start();

                byte[] buffer = new byte[1024];
                // V2 Header: String + \0
                byte[] header = (currentVoiceChannel + "\0").getBytes();

                while (isVoiceRunning) {
                    if (isMicMuted) {
                        Thread.sleep(100);
                        continue;
                    }

                    if (microphone.available() > 0) {
                        int bytesRead = microphone.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            byte[] packetData = new byte[header.length + bytesRead];
                            System.arraycopy(header, 0, packetData, 0, header.length);
                            System.arraycopy(buffer, 0, packetData, header.length, bytesRead);

                            DatagramPacket packet = new DatagramPacket(packetData, packetData.length, srvAddr, 1235);
                            voiceSocket.send(packet);
                        }
                    } else {
                        Thread.sleep(10); // Low CPU usage
                    }
                }
                microphone.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class AudioPlayback implements Runnable {
        @Override
        public void run() {
            try {
                // V2: 16kHz
                AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                currentSpeakers = (SourceDataLine) AudioSystem.getLine(info);
                currentSpeakers.open(format);
                currentSpeakers.start();

                byte[] buffer = new byte[4096];
                while (isVoiceRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    voiceSocket.receive(packet); // Blocking

                    byte[] data = packet.getData();
                    int len = packet.getLength();

                    // 1. Find null terminator to skip header
                    int offset = 0;
                    for (int i = 0; i < len; i++) {
                        if (data[i] == 0) {
                            offset = i + 1;
                            break;
                        }
                    }

                    if (offset < len && !isDeafened) {
                        currentSpeakers.write(data, offset, len - offset);
                    }
                }
                currentSpeakers.close();
            } catch (Exception e) {
                if (isVoiceRunning)
                    e.printStackTrace();
            }
        }
    }

    // ====================================================================================
    // --- MAIN ---
    // ====================================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}