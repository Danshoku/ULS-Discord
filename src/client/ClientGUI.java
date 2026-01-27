package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import javax.sound.sampled.*;

public class ClientGUI extends JFrame {

    // --- COULEURS ---
    private static final Color DISCORD_BG = Color.decode("#313338");
    private static final Color DISCORD_SIDEBAR = Color.decode("#2b2d31");
    private static final Color DISCORD_SERVER_STRIP = Color.decode("#1e1f22");
    private static final Color DISCORD_INPUT_BG = Color.decode("#383a40");
    private static final Color TEXT_HEADER = Color.decode("#f2f3f5");
    private static final Color TEXT_MUTED = Color.decode("#949ba4");
    private static final Color ONLINE_GREEN = Color.decode("#23a559");
    private static final Color BLURPLE = Color.decode("#5865F2");

    private static final Font FONT_MAIN = new Font("Segoe UI", Font.PLAIN, 15);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 15);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 16);

    private String username;
    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;

    private String activeServer = "HOME";
    private String currentContext = "HOME";

    private Map<String, List<String>> messageStorage = new HashMap<>();
    private Map<String, List<String>> serverChannels = new HashMap<>();

    private JPanel serverStrip;
    private JLabel sidebarTitle;
    private JList<String> sidebarList;
    private DefaultListModel<String> sidebarListModel;

    private DefaultListModel<String> friendListModel;
    private DefaultListModel<String> channelListModel;

    private JTextArea chatArea;
    private JTextField inputField;
    private JLabel chatTitleLabel;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private JButton addActionBtn;
    private JButton voiceStatusBtn;

    public ClientGUI() {
        setTitle("Discord Java Edition");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(DISCORD_BG);
        setLayout(new BorderLayout());

        friendListModel = new DefaultListModel<>();
        channelListModel = new DefaultListModel<>();

        buildInterface();
    }

    private void buildInterface() {
        // 1. GAUCHE EXTRÊME (Serveurs)
        serverStrip = new JPanel();
        serverStrip.setPreferredSize(new Dimension(72, 0));
        serverStrip.setBackground(DISCORD_SERVER_STRIP);
        serverStrip.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 10));

        JPanel homeBtn = createRoundButton(BLURPLE, "D", "Home / Amis");
        homeBtn.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                showHomeView();
            }
        });
        serverStrip.add(homeBtn);

        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setPreferredSize(new Dimension(40, 2));
        sep.setForeground(DISCORD_SIDEBAR);
        sep.setBackground(DISCORD_SIDEBAR);
        serverStrip.add(sep);

        JPanel addSrv = createRoundButton(DISCORD_BG, "+", "Créer un serveur");
        addSrv.setBorder(BorderFactory.createLineBorder(Color.GREEN));
        addSrv.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                createServerDialog();
            }
        });
        serverStrip.add(addSrv);

        add(serverStrip, BorderLayout.WEST);

        // 2. CENTRE GLOBAL
        JPanel mainContent = new JPanel(new BorderLayout());
        add(mainContent, BorderLayout.CENTER);

        // 3. SIDEBAR (Amis ou Salons)
        JPanel sidebarPanel = new JPanel(new BorderLayout());
        sidebarPanel.setPreferredSize(new Dimension(240, 0));
        sidebarPanel.setBackground(DISCORD_SIDEBAR);
        sidebarPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.decode("#1e1f22")));

        JPanel sidebarHeader = new JPanel(new BorderLayout());
        sidebarHeader.setBackground(DISCORD_SIDEBAR);
        sidebarHeader.setBorder(new EmptyBorder(10, 10, 10, 10));
        sidebarHeader.setPreferredSize(new Dimension(0, 50));

        sidebarTitle = new JLabel("MESSAGES PRIVÉS");
        sidebarTitle.setForeground(TEXT_HEADER);
        sidebarTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sidebarHeader.add(sidebarTitle, BorderLayout.CENTER);

        addActionBtn = new JButton("+");
        addActionBtn.setBackground(ONLINE_GREEN);
        addActionBtn.setForeground(Color.WHITE);
        addActionBtn.setFocusPainted(false);
        addActionBtn.setBorder(new EmptyBorder(2, 8, 2, 8));
        addActionBtn.addActionListener(e -> handleAddAction());
        voiceStatusBtn = new JButton("Déco Vocal");
        voiceStatusBtn.setBackground(Color.RED);
        voiceStatusBtn.setForeground(Color.WHITE);
        voiceStatusBtn.setFocusPainted(false);
        voiceStatusBtn.setBorder(new EmptyBorder(2, 5, 2, 5));
        voiceStatusBtn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        voiceStatusBtn.setVisible(false);
        voiceStatusBtn.addActionListener(e -> {
            leaveVoiceChannel();
            voiceStatusBtn.setVisible(false);
        });

        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        headerButtons.setBackground(DISCORD_SIDEBAR);
        headerButtons.add(voiceStatusBtn);
        headerButtons.add(addActionBtn);

        sidebarHeader.add(headerButtons, BorderLayout.EAST);

        sidebarPanel.add(sidebarHeader, BorderLayout.NORTH);

        sidebarListModel = friendListModel;
        sidebarList = new JList<>(sidebarListModel);
        sidebarList.setBackground(DISCORD_SIDEBAR);
        sidebarList.setForeground(TEXT_MUTED);
        sidebarList.setFont(FONT_MAIN);
        sidebarList.setCellRenderer(new PaddingRenderer(10));

        sidebarList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                String selected = sidebarList.getSelectedValue();
                if (selected == null)
                    return;

                if (activeServer.equals("HOME")) {
                    currentContext = "MP:" + selected;
                    chatTitleLabel.setText(selected);
                    updateChatDisplay();
                } else {
                    if (selected.startsWith("🔊")) {
                        joinVoiceChannel(activeServer + "|" + selected);
                        chatTitleLabel.setText("🔊 " + selected + " (Connecté)");
                        voiceStatusBtn.setVisible(true);
                    } else {
                        currentContext = activeServer + "|" + selected;
                        chatTitleLabel.setText(selected);
                        updateChatDisplay();
                    }
                }
            }
        });

        sidebarPanel.add(sidebarList, BorderLayout.CENTER);
        mainContent.add(sidebarPanel, BorderLayout.WEST);

        // 4. ZONE DE CHAT
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(DISCORD_BG);

        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(DISCORD_BG);
        chatHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.decode("#26272d")));
        chatHeader.setPreferredSize(new Dimension(0, 50));

        chatTitleLabel = new JLabel(" Accueil");
        chatTitleLabel.setForeground(TEXT_HEADER);
        chatTitleLabel.setFont(FONT_TITLE);
        chatTitleLabel.setBorder(new EmptyBorder(0, 20, 0, 0));
        chatHeader.add(chatTitleLabel, BorderLayout.CENTER);
        chatPanel.add(chatHeader, BorderLayout.NORTH);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setBackground(DISCORD_BG);
        chatArea.setForeground(TEXT_HEADER);
        chatArea.setFont(FONT_MAIN);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBorder(new EmptyBorder(20, 20, 20, 20));
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel inputContainer = new JPanel(new BorderLayout());
        inputContainer.setBackground(DISCORD_BG);
        inputContainer.setBorder(new EmptyBorder(20, 20, 20, 20));
        inputField = new JTextField();
        inputField.setBackground(DISCORD_INPUT_BG);
        inputField.setForeground(TEXT_HEADER);
        inputField.setCaretColor(TEXT_HEADER);
        inputField.setFont(FONT_MAIN);
        inputField.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        inputField.addActionListener(e -> sendMessage());
        inputContainer.add(inputField, BorderLayout.CENTER);
        chatPanel.add(inputContainer, BorderLayout.SOUTH);

        mainContent.add(chatPanel, BorderLayout.CENTER);

        // 5. LISTE DES MEMBRES (DROITE)
        JPanel membersPanel = new JPanel(new BorderLayout());
        membersPanel.setPreferredSize(new Dimension(240, 0));
        membersPanel.setBackground(DISCORD_SIDEBAR);

        JLabel membersTitle = new JLabel(" EN LIGNE");
        membersTitle.setForeground(TEXT_MUTED);
        membersTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        membersTitle.setBorder(new EmptyBorder(20, 15, 10, 0));
        membersPanel.add(membersTitle, BorderLayout.NORTH);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setBackground(DISCORD_SIDEBAR);
        userList.setCellRenderer(new DiscordMemberRenderer());
        membersPanel.add(new JScrollPane(userList), BorderLayout.CENTER);

        mainContent.add(membersPanel, BorderLayout.EAST);
    }

    // --- LOGIQUE MÉTIER ---

    private void createServerDialog() {
        String name = JOptionPane.showInputDialog(this, "Nom du nouveau serveur :");
        if (name != null && !name.trim().isEmpty()) {
            out.println("/create_server " + name.trim());
        }
    }

    private void handleAddAction() {
        if (activeServer.equals("HOME")) {
            String target = JOptionPane.showInputDialog(this, "Pseudo de l'ami à ajouter :");
            if (target != null)
                out.println("/friend add " + target.trim());
        } else {
            String[] options = { "Salon Textuel", "Salon Vocal" };
            int choice = JOptionPane.showOptionDialog(this, "Type de salon ?", "Créer", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

            String prefix = (choice == 1) ? "🔊" : "#";
            String chan = JOptionPane.showInputDialog(this, "Nom du salon :");
            if (chan != null)
                out.println("/create_channel " + activeServer + " " + prefix + chan.trim());
        }
    }

    private void addServerIcon(String name) {
        int hash = name.hashCode();
        Color c = new Color((hash & 0xFF0000) >> 16, (hash & 0x00FF00) >> 8, hash & 0x0000FF).brighter();
        JPanel srvBtn = createRoundButton(c, name.substring(0, 1).toUpperCase(), name);
        srvBtn.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                showServerView(name);
            }
        });
        serverStrip.add(srvBtn, serverStrip.getComponentCount() - 1);
        serverStrip.revalidate();
        serverStrip.repaint();
    }

    private void showHomeView() {
        activeServer = "HOME";
        currentContext = "HOME";
        sidebarTitle.setText("MESSAGES PRIVÉS");
        sidebarList.setModel(friendListModel);
        chatTitleLabel.setText("Accueil");
        updateChatDisplay();
    }

    private void showServerView(String serverName) {
        activeServer = serverName;
        sidebarTitle.setText(serverName.toUpperCase());
        channelListModel.clear();
        List<String> chans = serverChannels.getOrDefault(serverName, new ArrayList<>());
        for (String c : chans)
            channelListModel.addElement(c);
        sidebarList.setModel(channelListModel);

        if (!chans.isEmpty()) {
            currentContext = serverName + "|" + chans.get(0);
            chatTitleLabel.setText(chans.get(0));
        }
        updateChatDisplay();
    }

    private void updateChatDisplay() {
        chatArea.setText("");
        if (currentContext.startsWith("MP:")) {
            String target = currentContext.substring(3);
            for (Map.Entry<String, List<String>> entry : messageStorage.entrySet()) {
                String ctx = entry.getKey();
                if (ctx.startsWith("MP:") && ctx.contains(target)) {
                    for (String m : entry.getValue())
                        chatArea.append(m + "\n");
                }
            }
        } else {
            List<String> list = messageStorage.getOrDefault(currentContext, new ArrayList<>());
            for (String msg : list)
                chatArea.append(msg + "\n");
        }
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void sendMessage() {
        String msg = inputField.getText().trim();
        if (!msg.isEmpty() && out != null) {
            if (currentContext.equals("HOME")) {
                JOptionPane.showMessageDialog(this, "Sélectionnez un ami ou un salon !");
                return;
            }
            out.println(currentContext + "///" + msg);
            inputField.setText("");
        }
    }

    private JPanel createRoundButton(Color color, String text, String tooltip) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(color);
        p.setPreferredSize(new Dimension(48, 48));
        p.setCursor(new Cursor(Cursor.HAND_CURSOR));
        p.setToolTipText(tooltip);
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setForeground(Color.WHITE);
        l.setFont(new Font("Segoe UI", Font.BOLD, 20));
        p.add(l, BorderLayout.CENTER);
        p.setBorder(new EmptyBorder(0, 0, 0, 0));
        return p;
    }

    // --- CONNEXION ET RÉSEAU ---

    private void start() {
        UIManager.put("OptionPane.background", DISCORD_BG);
        UIManager.put("Panel.background", DISCORD_BG);
        UIManager.put("OptionPane.messageForeground", TEXT_HEADER);

        boolean connected = false;
        while (!connected) {
            // Création de la fenêtre de Login avec CHAMP IP
            JPanel loginPanel = new JPanel(new GridLayout(6, 1));
            loginPanel.setBackground(DISCORD_BG);

            JLabel ipLabel = new JLabel("IP du Serveur:");
            ipLabel.setForeground(TEXT_MUTED);
            // Par défaut "localhost", l'utilisateur doit changer ça s'il est sur un autre
            // PC
            JTextField ipField = new JTextField("localhost");

            JLabel uL = new JLabel("Pseudo:");
            uL.setForeground(TEXT_MUTED);
            JTextField userField = new JTextField();

            JLabel pL = new JLabel("Mot de passe:");
            pL.setForeground(TEXT_MUTED);
            JPasswordField passField = new JPasswordField();

            loginPanel.add(ipLabel);
            loginPanel.add(ipField);
            loginPanel.add(uL);
            loginPanel.add(userField);
            loginPanel.add(pL);
            loginPanel.add(passField);

            int res = JOptionPane.showConfirmDialog(this, loginPanel, "Connexion Discord", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (res != JOptionPane.OK_OPTION)
                System.exit(0);

            String ip = ipField.getText().trim();
            String u = userField.getText().trim();
            String p = new String(passField.getPassword()).trim();

            if (ip.isEmpty())
                ip = "127.0.0.1";
            if (u.isEmpty() || p.isEmpty())
                continue;

            try {
                // Connexion avec l'IP dynamique
                socket = new Socket(ip, 1234);

                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out.println(u);
                out.println(p);

                String resp = in.readLine();
                if ("SUCCESS".equals(resp)) {
                    username = u;
                    setTitle("Discord - " + username);
                    setVisible(true);
                    new Thread(new IncomingReader()).start();
                    connected = true;
                } else if (resp != null && resp.startsWith("FAIL:")) {
                    JOptionPane.showMessageDialog(this, resp.substring(5));
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Impossible de rejoindre le serveur à l'adresse : " + ip
                        + "\nVérifiez que le serveur est lancé et l'IP correcte.");
            }
        }
    }

    private class IncomingReader implements Runnable {
        public void run() {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.startsWith("NEW_SERVER:")) {
                        String srv = msg.substring(11);
                        SwingUtilities.invokeLater(() -> addServerIcon(srv));
                    } else if (msg.startsWith("NEW_CHANNEL:")) {
                        String[] p = msg.substring(12).split("\\|");
                        serverChannels.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p[1]);
                        if (activeServer.equals(p[0])) {
                            SwingUtilities.invokeLater(() -> {
                                channelListModel.clear();
                                for (String c : serverChannels.get(p[0]))
                                    channelListModel.addElement(c);
                            });
                        }
                    } else if (msg.startsWith("MSG:") || msg.startsWith("HISTORY:")) {
                        String payload = msg.contains("MSG:") ? msg.substring(4) : msg.substring(8);
                        int idx1 = payload.indexOf("///");
                        int idx2 = payload.indexOf("///", idx1 + 3);

                        if (idx1 > 0 && idx2 > 0) {
                            String ctx = payload.substring(0, idx1);
                            String snd = payload.substring(idx1 + 3, idx2);
                            String cnt = payload.substring(idx2 + 3);
                            String display = "[" + snd + "] " + cnt;

                            messageStorage.computeIfAbsent(ctx, k -> new ArrayList<>()).add(display);

                            if (currentContext.equals(ctx)
                                    || (currentContext.startsWith("MP:") && ctx.contains(username))) {
                                SwingUtilities.invokeLater(() -> updateChatDisplay());
                            }
                        }
                    } else if (msg.startsWith("FRIENDLIST:")) {
                        String raw = msg.substring(11);
                        if (!raw.isEmpty()) {
                            SwingUtilities.invokeLater(() -> {
                                friendListModel.clear();
                                for (String f : raw.split(","))
                                    friendListModel.addElement(f);
                            });
                        }
                    } else if (msg.startsWith("FRIEND_REQ:")) {
                        String req = msg.substring(11);
                        SwingUtilities.invokeLater(() -> {
                            if (JOptionPane.showConfirmDialog(ClientGUI.this, req + " vous demande en ami !", "Ami",
                                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                out.println("/friend accept " + req);
                            }
                        });
                    } else if (msg.startsWith("USERLIST:")) {
                        String[] users = msg.equals("USERLIST:") ? new String[0] : msg.substring(9).split(",");
                        SwingUtilities.invokeLater(() -> {
                            userListModel.clear();
                            for (String u : users)
                                userListModel.addElement(u);
                        });
                    }
                }
            } catch (IOException e) {
            }
        }
    }

    // --- CLASSES DE RENDU VISUEL ---

    static class PaddingRenderer extends DefaultListCellRenderer {
        private int padding;

        public PaddingRenderer(int padding) {
            this.padding = padding;
        }

        public PaddingRenderer() {
            this(10);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(new EmptyBorder(padding / 2, padding, padding / 2, padding));
            if (isSelected) {
                label.setBackground(Color.decode("#3f4147"));
                label.setForeground(Color.WHITE);
            } else {
                label.setBackground(DISCORD_SIDEBAR);
                label.setForeground(Color.decode("#949ba4"));
            }
            return label;
        }
    }

    static class DiscordMemberRenderer extends JPanel implements ListCellRenderer<String> {
        private JLabel nameLabel;
        private JPanel avatarPanel;

        public DiscordMemberRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBackground(DISCORD_SIDEBAR);
            setBorder(new EmptyBorder(5, 10, 5, 10));
            avatarPanel = new JPanel() {
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(BLURPLE);
                    g2.fillOval(0, 0, 32, 32);
                    g2.setColor(DISCORD_SIDEBAR);
                    g2.fillOval(22, 22, 14, 14);
                    g2.setColor(ONLINE_GREEN);
                    g2.fillOval(24, 24, 10, 10);
                }
            };
            avatarPanel.setPreferredSize(new Dimension(35, 35));
            avatarPanel.setOpaque(false);
            nameLabel = new JLabel();
            nameLabel.setForeground(TEXT_MUTED);
            nameLabel.setFont(FONT_BOLD);
            add(avatarPanel, BorderLayout.WEST);
            add(nameLabel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(value);
            if (isSelected) {
                setBackground(Color.decode("#3f4147"));
                nameLabel.setForeground(TEXT_HEADER);
            } else {
                setBackground(DISCORD_SIDEBAR);
                nameLabel.setForeground(TEXT_MUTED);
            }
            return this;
        }
    }

    // --- PARTIE VOCALE ---

    private DatagramSocket voiceSocket;
    private boolean isVoiceRunning = false;
    private String currentVoiceChannel = null;
    private Thread captureThread;
    private Thread playbackThread;

    private void joinVoiceChannel(String fullChannelName) {
        if (isVoiceRunning)
            leaveVoiceChannel();

        try {
            // Création socket UDP (Port aléatoire)
            voiceSocket = new DatagramSocket();
            isVoiceRunning = true;
            currentVoiceChannel = fullChannelName;

            InetAddress srvAddr = socket.getInetAddress();

            // Lancement des threads Audio
            captureThread = new Thread(new AudioCapture(srvAddr));
            playbackThread = new Thread(new AudioPlayback());

            captureThread.start();
            playbackThread.start();

            // Envoi d'un premier paquet pour s'enregistrer au serveur
            // "Srv|🔊Chan" + 0 + (vide)
            byte[] header = (fullChannelName + "\0").getBytes();
            DatagramPacket init = new DatagramPacket(header, header.length, srvAddr, 1235);
            voiceSocket.send(init);

            JOptionPane.showMessageDialog(this, "Connecté au salon vocal : " + fullChannelName.split("\\|")[1]);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Erreur connexion vocal : " + e.getMessage());
        }
    }

    private void leaveVoiceChannel() {
        isVoiceRunning = false;
        if (voiceSocket != null && !voiceSocket.isClosed())
            voiceSocket.close();
        currentVoiceChannel = null;
    }

    private class AudioCapture implements Runnable {
        InetAddress srvAddr;

        public AudioCapture(InetAddress a) {
            this.srvAddr = a;
        }

        @Override
        public void run() {
            try {
                AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, true);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

                if (!AudioSystem.isLineSupported(info)) {
                    System.err.println("Micro non supporté");
                    return;
                }

                TargetDataLine micro = (TargetDataLine) AudioSystem.getLine(info);
                micro.open(format);
                micro.start();

                System.out.println("Micro ouvert.");
                byte[] buffer = new byte[1024];
                byte[] header = (currentVoiceChannel + "\0").getBytes(); // Header toujours envoyé pour identification
                                                                         // simpliste

                while (isVoiceRunning) {
                    int read = micro.read(buffer, 0, buffer.length);
                    // Construction paquet : Header + Audio
                    // C'est pas opti (copie mémoire), mais fiable
                    byte[] packetData = new byte[header.length + read];
                    System.arraycopy(header, 0, packetData, 0, header.length);
                    System.arraycopy(buffer, 0, packetData, header.length, read);

                    DatagramPacket packet = new DatagramPacket(packetData, packetData.length, srvAddr, 1235);
                    if (voiceSocket != null && !voiceSocket.isClosed())
                        voiceSocket.send(packet);
                }
                micro.close();
            } catch (Exception e) {
                if (isVoiceRunning)
                    e.printStackTrace();
            }
        }
    }

    private class AudioPlayback implements Runnable {
        @Override
        public void run() {
            try {
                AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, true);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info);
                speakers.open(format);
                speakers.start();

                byte[] buffer = new byte[4096];

                while (isVoiceRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    if (voiceSocket != null && !voiceSocket.isClosed()) {
                        voiceSocket.receive(packet);

                        // On reçoit Header + Audio
                        byte[] data = packet.getData();
                        int len = packet.getLength();

                        // On saute le header
                        int offset = 0;
                        for (int i = 0; i < len; i++) {
                            if (data[i] == 0) {
                                offset = i + 1;
                                break;
                            }
                        }

                        if (offset < len) {
                            speakers.write(data, offset, len - offset);
                        }
                    }
                }
                speakers.close();
            } catch (Exception e) {
                if (isVoiceRunning)
                    e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientGUI().start());
    }
}