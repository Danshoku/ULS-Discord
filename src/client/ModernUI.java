package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.*;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModernUI {

    private final ClientGUI app;

    // --- UI COMPONENTS ---
    JPanel serverStrip;
    JPanel sidebarPanel;
    JPanel sidebarHeader;
    JList<String> sidebarList;
    JList<String> userList;
    JPanel chatPanel;
    JPanel chatHeader;
    JTextPane chatArea;
    JPanel inputContainer;
    JTextField inputField;
    JLabel chatTitleLabel;
    JButton sidebarTitle;
    JPanel membersPanel;
    JButton serverSettingsBtn;
    JPanel sidebarBottomContainer;
    JPanel userBar;
    JPanel voicePanel;
    JLabel voiceChannelLabel;
    JLabel userNameLabel;
    JLabel userStatusLabel;
    JButton micBtn, deafenBtn, settingsBtn;

    // --- COULEURS ET FONTS (GLASSMORPHISM) ---
    // Palette
    private static final Color GLASS_PANEL_BG = new Color(30, 32, 40, 200);
    private static final Color GLASS_BORDER = new Color(255, 255, 255, 25);
    private static final Color BLURPLE = new Color(88, 101, 242);
    private static final Color ONLINE_GREEN = new Color(35, 165, 89);
    private static final Color PACMAN_YELLOW = new Color(255, 225, 0);
    private static final Color TEXT_HEADER = Color.WHITE;
    private static final Color TEXT_MUTED = new Color(180, 184, 190);

    // Fonts
    private static final Font FONT_MAIN = new Font("Segoe UI", Font.PLAIN, 15);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_PLAIN = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_UI = new Font("Segoe UI", Font.PLAIN, 13);

    public ModernUI(ClientGUI app) {
        this.app = app;
    }

    public void initUI() {
        // --- 0. BACKGROUND (Radial Gradient) ---
        JPanel backgroundPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Base Sombre
                g2.setColor(new Color(17, 24, 39));
                g2.fillRect(0, 0, w, h);

                // Radial Gradients (Simulation HTML Concept)
                // Top-Left (Indigi)
                drawRadial(g2, 0, 0, w / 2, new Color(79, 70, 229, 100));
                // Top-Right (Pink)
                drawRadial(g2, w, 0, w / 2, new Color(236, 72, 153, 100));
                // Bottom-Right (Blue)
                drawRadial(g2, w, h, w / 2, new Color(59, 130, 246, 100));
                // Bottom-Left (Purple)
                drawRadial(g2, 0, h, w / 2, new Color(139, 92, 246, 100));
            }

            private void drawRadial(Graphics2D g2, int x, int y, int radius, Color color) {
                float[] dist = { 0.0f, 1.0f };
                Color[] colors = { color, new Color(0, 0, 0, 0) };
                RadialGradientPaint p = new RadialGradientPaint(new Point(x, y), radius, dist, colors);
                g2.setPaint(p);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        app.setContentPane(backgroundPanel);

        // --- 1. BARRE SERVEURS (Navigation Gauche) ---
        serverStrip = new GlassPanel(new FlowLayout(FlowLayout.CENTER, 0, 15)); // Spacing between icons
        serverStrip.setPreferredSize(new Dimension(80, 0));

        // Wrapper pour l'effet flottant
        JPanel stripWrapper = new JPanel(new BorderLayout());
        stripWrapper.setOpaque(false);
        stripWrapper.setBorder(new EmptyBorder(15, 15, 15, 0));
        stripWrapper.add(serverStrip, BorderLayout.CENTER);

        // Bouton Home (Logo Discord / D)
        JPanel homeBtn = new JPanel(new GridBagLayout());
        homeBtn.setPreferredSize(new Dimension(80, 60)); // Wrapper Rect
        homeBtn.setOpaque(false);
        homeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JLabel homeLabel = new JLabel("D", SwingConstants.CENTER);
        homeLabel.setForeground(Color.WHITE);
        homeLabel.setFont(new Font("Segoe UI Black", Font.BOLD, 22));

        JPanel homeBg = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BLURPLE);
                g2.fillRoundRect(0, 0, 50, 50, 20, 20); // Squircle
            }
        };
        homeBg.setPreferredSize(new Dimension(50, 50));
        homeBg.setOpaque(false);
        homeBg.add(homeLabel);

        homeBtn.add(homeBg);
        homeBtn.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                app.showHomeView();
            }
        });

        serverStrip.add(homeBtn);

        // Séparateur
        JPanel sepWrapper = new JPanel(new GridBagLayout());
        sepWrapper.setPreferredSize(new Dimension(80, 15));
        sepWrapper.setOpaque(false);
        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setPreferredSize(new Dimension(30, 2));
        sep.setForeground(new Color(255, 255, 255, 30));
        sep.setBackground(new Color(0, 0, 0, 0));
        sepWrapper.add(sep);
        serverStrip.add(sepWrapper);

        // Bouton Add (+)
        JPanel addWrapper = new JPanel(new GridBagLayout());
        addWrapper.setPreferredSize(new Dimension(80, 60));
        addWrapper.setOpaque(false);
        GlassButton addSrv = new GlassButton("+");
        addSrv.setFont(new Font("Segoe UI", Font.BOLD, 24));
        addSrv.setForeground(ONLINE_GREEN);
        addSrv.setPreferredSize(new Dimension(50, 50));
        addSrv.addActionListener(e -> app.createServerDialog());
        addWrapper.add(addSrv);
        serverStrip.add(addWrapper);

        app.add(stripWrapper, BorderLayout.WEST);

        // --- CONTENEUR PRINCIPAL (Sidebar + Chat + Membres) ---
        JPanel mainContainer = new JPanel(new BorderLayout(15, 0)); // Gap horizontal de 15px
        mainContainer.setOpaque(false);
        mainContainer.setBorder(new EmptyBorder(15, 15, 15, 15));

        app.add(mainContainer, BorderLayout.CENTER);

        // --- 2. SIDEBAR (Canaux) ---
        sidebarPanel = new GlassPanel(new BorderLayout());
        sidebarPanel.setPreferredSize(new Dimension(240, 0));

        // Header Sidebar
        sidebarHeader = new JPanel(new BorderLayout());
        sidebarHeader.setOpaque(false);
        sidebarHeader.setBorder(new EmptyBorder(15, 15, 15, 15));
        sidebarHeader.setPreferredSize(new Dimension(0, 60));

        sidebarTitle = new GlassButton("MESSAGES PRIVÉS");
        sidebarTitle.setHorizontalAlignment(SwingConstants.LEFT);
        sidebarTitle.setBorder(null);
        sidebarTitle.addActionListener(e -> app.handleServerMenu());

        sidebarHeader.add(sidebarTitle, BorderLayout.CENTER);

        // Actions Header
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actionPanel.setOpaque(false);

        serverSettingsBtn = new GlassButton("⚙");
        serverSettingsBtn.setVisible(false);
        serverSettingsBtn.addActionListener(e -> app.openRoleManager());

        actionPanel.add(serverSettingsBtn);
        // addBtn removed to focus on Server Name Menu
        sidebarHeader.add(actionPanel, BorderLayout.EAST);

        sidebarPanel.add(sidebarHeader, BorderLayout.NORTH);

        // Liste Sidebar
        sidebarList = new JList<>(app.sidebarListModel);
        sidebarList.setOpaque(false);
        sidebarList.setBackground(new Color(0, 0, 0, 0));
        sidebarList.setCellRenderer(new SidebarRenderer());
        sidebarList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = sidebarList.locationToIndex(e.getPoint());
                    if (index != -1 && sidebarList.getCellBounds(index, index).contains(e.getPoint())) {
                        sidebarList.setSelectedIndex(index);
                        String val = sidebarList.getSelectedValue();
                        if (val != null && (val.startsWith("#") || val.startsWith("🔊"))) {
                            JPopupMenu menu = new JPopupMenu();

                            JMenuItem rename = new JMenuItem("Renommer le salon");
                            rename.addActionListener(Mx -> app.renameChannel(val));
                            menu.add(rename);

                            JMenuItem del = new JMenuItem("Supprimer le salon");
                            del.setForeground(Color.RED);
                            del.addActionListener(Mx -> app.deleteChannel(val));
                            menu.add(del);
                            menu.show(sidebarList, e.getX(), e.getY());
                        }
                    }
                } else {
                    app.handleSidebarClick();
                }
            }
        });

        JScrollPane scrollSide = new JScrollPane(sidebarList);
        scrollSide.setOpaque(false);
        scrollSide.getViewport().setOpaque(false);
        scrollSide.setBorder(null);
        scrollSide.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollSide.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        scrollSide.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        sidebarPanel.add(scrollSide, BorderLayout.CENTER);

        // User Bar (Bottom)
        sidebarBottomContainer = new GlassPanel(new BorderLayout());
        sidebarBottomContainer.setOpaque(false); // Le GlassPanel gère le fond
        buildVoicePanel();
        // We ensure voicePanel uses local field in ClientGUI for now, but we will move
        // it.
        // For this step, we assume app.voicePanel is still valid until we move the
        // method.
        // ACTUALLY, we are moving fields to ModernUI, so app.voicePanel will be invalid
        // soon.
        // We need to move buildVoicePanel logic TO ModernUI or return a panel.

        // Let's assume for a moment we will fix methods later.
        // But we must fix 'app.voicePanel' here to 'voicePanel' if we moved the field.
        // Since buildVoicePanel is in ClientGUI, it assigns to ClientGUI.voicePanel.
        // We need to pull that logic here.

        // TEMPORARY FIX: Assign local field from app's field after build (Strategy:
        // incremental)
        // Better Strategy: Move buildVoicePanel and buildUserBar logic to ModernUI NOW.
        buildVoicePanel();
        voicePanel.setOpaque(false);
        sidebarBottomContainer.add(voicePanel, BorderLayout.NORTH);

        buildUserBar();
        userBar.setOpaque(false);
        sidebarBottomContainer.add(userBar, BorderLayout.SOUTH);

        sidebarPanel.add(sidebarBottomContainer, BorderLayout.SOUTH);

        mainContainer.add(sidebarPanel, BorderLayout.WEST);

        // --- 3. CHAT AREA ---
        chatPanel = new GlassPanel(new BorderLayout());

        // Header Chat
        chatHeader = new JPanel(new BorderLayout());
        chatHeader.setOpaque(false);
        chatHeader.setBorder(new EmptyBorder(10, 20, 10, 20));
        chatHeader.setPreferredSize(new Dimension(0, 60));

        chatTitleLabel = new JLabel("# Accueil");
        chatTitleLabel.setForeground(Color.WHITE);
        chatTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        chatHeader.add(chatTitleLabel, BorderLayout.CENTER);

        chatPanel.add(chatHeader, BorderLayout.NORTH);

        // Zone Texte
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setContentType("text/html");
        chatArea.setOpaque(false);
        chatArea.setBackground(new Color(0, 0, 0, 0));

        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule(
                "body { font-family: 'Segoe UI', sans-serif; font-size: 14px; color: #e5e7eb; margin: 15px; }");
        styleSheet.addRule("a { color: #3b82f6; text-decoration: none; font-weight: bold; }");
        styleSheet.addRule(".timestamp { color: #9ca3af; font-size: 10px; margin-left: 8px; }");
        styleSheet.addRule(".username { color: #fff; font-weight: bold; }");
        chatArea.setEditorKit(kit);

        chatArea.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ex) {
                }
            }
        });

        JScrollPane scrollChat = new JScrollPane(chatArea);
        scrollChat.setOpaque(false);
        scrollChat.getViewport().setOpaque(false);
        scrollChat.setBorder(null);
        scrollChat.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollChat.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        chatPanel.add(scrollChat, BorderLayout.CENTER);

        // Input
        inputContainer = new JPanel(new BorderLayout(10, 0));
        inputContainer.setOpaque(false);
        inputContainer.setBorder(new EmptyBorder(20, 20, 20, 20));

        GlassButton uploadBtn = new GlassButton("+");
        uploadBtn.addActionListener(e -> app.sendImage());

        inputField = new JTextField();
        inputField.setOpaque(false);
        inputField.setForeground(Color.WHITE);
        inputField.setCaretColor(Color.WHITE);
        inputField.setFont(FONT_MAIN);
        inputField.setBorder(BorderFactory.createCompoundBorder(
                new javax.swing.border.LineBorder(new Color(255, 255, 255, 30), 1, true),
                new EmptyBorder(10, 15, 10, 15)));
        inputField.addActionListener(e -> app.sendMessage());
        inputField.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V) {
                    if (app.pasteImageFromClipboard())
                        e.consume();
                }
            }
        });

        inputContainer.add(uploadBtn, BorderLayout.WEST);
        inputContainer.add(inputField, BorderLayout.CENTER);
        chatPanel.add(inputContainer, BorderLayout.SOUTH);

        mainContainer.add(chatPanel, BorderLayout.CENTER);

        // --- 4. MEMBRES (Droite) ---
        membersPanel = new GlassPanel(new BorderLayout());
        membersPanel.setPreferredSize(new Dimension(240, 0));

        JLabel memTitle = new JLabel("MEMBRES");
        memTitle.setForeground(TEXT_MUTED);
        memTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        memTitle.setBorder(new EmptyBorder(20, 20, 10, 0));
        membersPanel.add(memTitle, BorderLayout.NORTH);

        userList = new JList<>(app.userListModel);
        userList.setOpaque(false);
        userList.setBackground(new Color(0, 0, 0, 0));
        userList.setCellRenderer(new DiscordMemberRenderer());

        JScrollPane scrollUsers = new JScrollPane(userList);
        scrollUsers.setOpaque(false);
        scrollUsers.getViewport().setOpaque(false);
        scrollUsers.setBorder(null);
        scrollUsers.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollUsers.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        membersPanel.add(scrollUsers, BorderLayout.CENTER);

        // Btn PacMan
        JPanel gamePanel = new JPanel(new BorderLayout());
        gamePanel.setOpaque(false);
        gamePanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JButton pacBtn = new JButton("PAC-MAN");
        pacBtn.setBackground(PACMAN_YELLOW);
        pacBtn.setForeground(Color.BLACK);
        pacBtn.setFont(new Font("Segoe UI Black", Font.BOLD, 14));
        pacBtn.setFocusPainted(false);
        pacBtn.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        pacBtn.addActionListener(e -> app.launchGame());

        gamePanel.add(pacBtn, BorderLayout.CENTER);
        membersPanel.add(gamePanel, BorderLayout.SOUTH);

        mainContainer.add(membersPanel, BorderLayout.EAST);
    }

    private void buildVoicePanel() {
        voicePanel = new JPanel(new BorderLayout());
        voicePanel.setOpaque(false);
        voicePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(255, 255, 255, 10)),
                new EmptyBorder(8, 10, 8, 10)));
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
        hangupBtn.setOpaque(false);
        hangupBtn.setContentAreaFilled(false);
        hangupBtn.setBorderPainted(false);
        hangupBtn.setFocusPainted(false);
        hangupBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        hangupBtn.setToolTipText("Déconnecter");
        hangupBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                hangupBtn.setOpaque(true);
                hangupBtn.setBackground(new Color(218, 55, 60)); // RED_ERROR
            }

            public void mouseExited(MouseEvent e) {
                hangupBtn.setOpaque(false);
            }
        });
        hangupBtn.addActionListener(e -> app.leaveVoiceChannel());

        voicePanel.add(infoP, BorderLayout.CENTER);
        voicePanel.add(hangupBtn, BorderLayout.EAST);
    }

    private void buildUserBar() {
        userBar = new JPanel(new BorderLayout());
        userBar.setOpaque(false);
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

                // Default Avatar
                g2.setColor(ONLINE_GREEN);
                g2.fillOval(0, 0, 32, 32);
                g2.setColor(new Color(30, 32, 40)); // Fake BG
                g2.fillOval(22, 22, 12, 12);

                // Status Dot
                g2.setColor(ONLINE_GREEN);
                g2.fillOval(24, 24, 8, 8);
            }
        };
        avatar.setPreferredSize(new Dimension(32, 32));
        avatar.setOpaque(false);

        JPanel textPanel = new JPanel(new GridLayout(2, 1));
        textPanel.setOpaque(false);

        userNameLabel = new JLabel("Chargement...");
        userNameLabel.setForeground(TEXT_HEADER);
        userNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        userStatusLabel = new JLabel("En ligne");
        userStatusLabel.setForeground(TEXT_MUTED);
        userStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        textPanel.add(userNameLabel);
        textPanel.add(userStatusLabel);

        infoPanel.add(avatar);
        infoPanel.add(textPanel);

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controlsPanel.setOpaque(false);

        micBtn = createIconButton("🎤", "Rendre muet");
        micBtn.addActionListener(e -> app.toggleMic());

        deafenBtn = createIconButton("🎧", "Mettre en sourdine");
        deafenBtn.addActionListener(e -> app.toggleDeafen());

        settingsBtn = createIconButton("⚙", "Paramètres");
        settingsBtn.addActionListener(e -> app.openSettingsDialog());

        controlsPanel.add(micBtn);
        controlsPanel.add(deafenBtn);
        controlsPanel.add(settingsBtn);

        userBar.add(infoPanel, BorderLayout.CENTER);
        userBar.add(controlsPanel, BorderLayout.EAST);
    }

    private JButton createIconButton(String icon, String tooltip) {
        JButton btn = new JButton(icon);
        btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        btn.setForeground(TEXT_HEADER);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(32, 32));

        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setOpaque(true);
                btn.setBackground(new Color(255, 255, 255, 30));
            }

            public void mouseExited(MouseEvent e) {
                btn.setOpaque(false);
            }
        });
        return btn;
    }

    public void addServerIcon(String name) {
        for (Component c : serverStrip.getComponents()) {
            if (c instanceof JPanel && name.equals(((JPanel) c).getToolTipText()))
                return;
        }

        // Generate color based on name hash
        int hash = name.hashCode();
        Color c = new Color((hash & 0xFF0000) >> 16, (hash & 0x00FF00) >> 8, hash & 0x0000FF);
        // Ensure it's not too dark
        if (c.getRed() + c.getGreen() + c.getBlue() < 300)
            c = c.brighter();

        JPanel srvWrapper = createRoundButton(c, name.substring(0, 1).toUpperCase(), name);
        // The mouse listener must be on the inner button or the wrapper?
        // createRoundButton returns the wrapper now. The inner button needs the
        // listener?
        // Let's attach to the wrapper for bigger hit area or find inner?
        // Better: createRoundButton returns wrapper, but we attach listener to wrapper?
        // Or createRoundButton attaches listener? No, we attach specific listener here.
        // We will attach listener to the wrapper.
        srvWrapper.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                app.showServerView(name);
            }
        });

        // Add before the last element (assuming last is '+')
        // We have: Home, Sep, [Servers...], Add
        // Wait, current implementation:
        // serverStrip.add(homeBtn);
        // serverStrip.add(sep);
        // serverStrip.add(addSrv);
        // So we want to add before 'addSrv'.
        // Add before the last element (assuming last is nested in wrapper)
        // We have: Home, Sep, [Servers...], Add
        int count = serverStrip.getComponentCount();
        if (count > 0) {
            serverStrip.add(srvWrapper, count - 1);
        } else {
            serverStrip.add(srvWrapper);
        }

        serverStrip.revalidate();
        serverStrip.repaint();
    }

    private JPanel createRoundButton(Color color, String text, String tooltip) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setPreferredSize(new Dimension(80, 60));
        wrapper.setOpaque(false);
        wrapper.setToolTipText(tooltip); // Set tooltip on wrapper for addServerIcon check

        JPanel p = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                super.paintComponent(g);
            }
        };
        p.setOpaque(false);
        p.setBackground(new Color(0, 0, 0, 0));
        p.setPreferredSize(new Dimension(48, 48));
        p.setCursor(new Cursor(Cursor.HAND_CURSOR));
        // p.setToolTipText(tooltip); // Tooltip on inner?

        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setForeground(Color.WHITE);
        l.setFont(new Font("Segoe UI", Font.BOLD, 20));
        p.add(l, BorderLayout.CENTER);

        wrapper.add(p);
        return wrapper;
    }

    public void applyTheme(boolean isDark) {
        Color bg, sidebar, strip, input, text, muted, userBarBg, voiceBg;
        if (isDark) {
            bg = new Color(30, 32, 40, 200); // Standard Glass
            sidebar = new Color(30, 32, 40, 200);
            strip = new Color(20, 20, 25, 200);
            input = new Color(0, 0, 0, 100);
            text = Color.WHITE;
            muted = new Color(180, 184, 190);
            userBarBg = new Color(0, 0, 0, 0);
            voiceBg = new Color(0, 0, 0, 0);
        } else {
            // Light Theme (Glassy)
            bg = new Color(255, 255, 255, 220);
            sidebar = new Color(242, 243, 245, 200);
            strip = new Color(227, 229, 232, 200);
            input = new Color(255, 255, 255, 180);
            text = Color.BLACK;
            muted = Color.GRAY;
            userBarBg = new Color(0, 0, 0, 0);
            voiceBg = new Color(0, 0, 0, 0);
        }

        // Update variables (if we were using dynamic vars, but we operate on components
        // directly)
        app.getContentPane().setBackground(isDark ? new Color(20, 20, 25) : Color.WHITE);

        serverStrip.setBackground(strip);
        sidebarPanel.setBackground(sidebar);
        sidebarHeader.setBackground(sidebar);
        sidebarTitle.setForeground(text);
        sidebarList.setForeground(text);
        sidebarBottomContainer.setBackground(sidebar);

        chatPanel.setBackground(bg);
        chatHeader.setBackground(bg);
        chatTitleLabel.setForeground(text);
        chatArea.setBackground(new Color(0, 0, 0, 0)); // Always transparent

        inputContainer.setBackground(bg);
        inputField.setBackground(input);
        inputField.setForeground(text);
        inputField.setCaretColor(text);

        membersPanel.setBackground(sidebar);
        userList.setForeground(text);

        userNameLabel.setForeground(text);

        micBtn.setForeground(app.isMicMuted ? new Color(218, 55, 60) : text);
        deafenBtn.setForeground(app.isDeafened ? new Color(218, 55, 60) : text);
        settingsBtn.setForeground(text);

        app.repaint();
    }

    void handleServerMenu() {
        if (!app.activeServer.equals("HOME")) {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem inviteItem = new JMenuItem("Inviter des gens");
            inviteItem.addActionListener(x -> app.out.println("/create_invite " + app.activeServer));
            JMenuItem createChanItem = new JMenuItem("Créer un salon");
            createChanItem.addActionListener(x -> handleAddAction());
            JMenuItem leaveItem = new JMenuItem("Quitter le serveur");
            leaveItem.setForeground(Color.RED);
            leaveItem.addActionListener(x -> {
                if (JOptionPane.showConfirmDialog(app, "Quitter " + app.activeServer + " ?", "Confirmer",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    app.out.println("/leave " + app.activeServer);
                }
            });
            menu.add(inviteItem);
            menu.add(createChanItem);
            menu.addSeparator();
            menu.add(leaveItem);
            menu.show(sidebarTitle, 0, sidebarTitle.getHeight());
        }
    }

    void createServerDialog() {
        Object[] options = { "Créer un serveur", "Rejoindre un serveur" };
        int n = JOptionPane.showOptionDialog(app, "Que voulez-vous faire ?", "Gestion Serveur",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (n == 0) {
            String name = JOptionPane.showInputDialog(app, "Nom du nouveau serveur (Unique) :");
            if (name != null && !name.trim().isEmpty())
                app.out.println("/create_server " + name.trim());
        } else if (n == 1) {
            String code = JOptionPane.showInputDialog(app, "Code d'invitation :");
            if (code != null)
                app.out.println("/join " + code.trim());
        }
    }

    void handleAddAction() {
        if (app.activeServer.equals("HOME")) {
            String target = JOptionPane.showInputDialog(app, "Pseudo de l'ami à ajouter :");
            if (target != null)
                app.out.println("/friend add " + target.trim());
        } else {
            String[] options = { "Salon Textuel (#)", "Salon Vocal (🔊)" };
            int choice = JOptionPane.showOptionDialog(app, "Type de salon ?", "Créer", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            String prefix = (choice == 1) ? "🔊" : "#";
            String chan = JOptionPane.showInputDialog(app, "Nom du salon :");
            if (chan != null)
                app.out.println(
                        "/create_channel " + app.activeServer + " " + (prefix.equals("🔊") ? "🔊" : "") + chan.trim());
        }
    }

    void openRoleManager() {
        if (app.activeServer.equals("HOME"))
            return;

        JDialog roleDialog = new JDialog(app, "Paramètres du Serveur - " + app.activeServer, true);
        roleDialog.setSize(700, 500);
        roleDialog.setLocationRelativeTo(app);
        roleDialog.getContentPane().setBackground(Color.decode("#313338"));
        roleDialog.setLayout(new BorderLayout());

        JPanel leftPanel = new JPanel();
        leftPanel.setPreferredSize(new Dimension(200, 0));
        leftPanel.setBackground(Color.decode("#2b2d31"));
        leftPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        JLabel title = new JLabel("RÔLES");
        title.setForeground(Color.decode("#949ba4"));
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setBorder(new EmptyBorder(10, 10, 10, 10));
        leftPanel.add(title);
        JLabel tabRole = new JLabel("Gestion des Rôles");
        tabRole.setForeground(Color.WHITE);
        tabRole.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tabRole.setBorder(new EmptyBorder(5, 10, 5, 10));
        tabRole.setOpaque(true);
        tabRole.setBackground(Color.decode("#3f4147"));
        leftPanel.add(tabRole);
        roleDialog.add(leftPanel, BorderLayout.WEST);

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(Color.decode("#313338"));
        contentPanel.setBorder(new EmptyBorder(20, 40, 20, 40));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.decode("#313338"));
        JLabel pageTitle = new JLabel("Rôles du serveur");
        pageTitle.setForeground(Color.WHITE);
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
        JButton createBtn = new JButton("Création de rôle");
        createBtn.setBackground(new Color(88, 101, 242));
        createBtn.setForeground(Color.WHITE);
        createBtn.setFocusPainted(false);
        header.add(pageTitle, BorderLayout.WEST);
        header.add(createBtn, BorderLayout.EAST);
        contentPanel.add(header, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridLayout(2, 1, 0, 20));
        formPanel.setBackground(Color.decode("#313338"));
        formPanel.setBorder(new EmptyBorder(20, 0, 0, 0));

        JPanel createPanel = new JPanel(new BorderLayout());
        createPanel.setBackground(Color.decode("#383a40"));
        createPanel.setBorder(BorderFactory.createTitledBorder(null, "NOUVEAU RÔLE", 0, 0,
                new Font("Segoe UI", Font.BOLD, 10), Color.decode("#949ba4")));
        JPanel inputs = new JPanel(new GridLayout(5, 1, 5, 5));
        inputs.setBackground(Color.decode("#383a40"));
        inputs.setBorder(new EmptyBorder(10, 10, 10, 10));
        JTextField roleNameField = new JTextField();
        roleNameField.setBorder(BorderFactory.createTitledBorder("Nom du rôle"));
        JCheckBox pAdmin = new JCheckBox("Administrateur (Tout faire)");
        JCheckBox pBan = new JCheckBox("Bannir des membres");
        JCheckBox pKick = new JCheckBox("Expulser des membres");
        JCheckBox pChan = new JCheckBox("Gérer les salons (Créer/Supprimer)");
        for (JCheckBox cb : new JCheckBox[] { pAdmin, pBan, pKick, pChan }) {
            cb.setBackground(Color.decode("#383a40"));
            cb.setForeground(Color.WHITE);
            cb.setFocusPainted(false);
        }
        inputs.add(roleNameField);
        inputs.add(pAdmin);
        inputs.add(pBan);
        inputs.add(pKick);
        inputs.add(pChan);
        createPanel.add(inputs, BorderLayout.CENTER);

        createBtn.addActionListener(e -> {
            String rName = roleNameField.getText().trim();
            if (rName.isEmpty())
                return;
            int perms = 0;
            if (pAdmin.isSelected())
                perms |= ClientGUI.PERM_ADMIN;
            if (pBan.isSelected())
                perms |= ClientGUI.PERM_BAN;
            if (pKick.isSelected())
                perms |= ClientGUI.PERM_KICK;
            if (pChan.isSelected())
                perms |= ClientGUI.PERM_CHANNELS;
            app.out.println("/role create " + app.activeServer + " " + rName + " " + perms);
            JOptionPane.showMessageDialog(roleDialog, "Commande envoyée : Création du rôle " + rName);
            roleNameField.setText("");
        });

        JPanel assignPanel = new JPanel(new BorderLayout());
        assignPanel.setBackground(Color.decode("#383a40"));
        assignPanel.setBorder(BorderFactory.createTitledBorder(null, "ATTRIBUER UN RÔLE", 0, 0,
                new Font("Segoe UI", Font.BOLD, 10), Color.decode("#949ba4")));
        JPanel assignInputs = new JPanel(new GridLayout(3, 1, 5, 5));
        assignInputs.setBackground(Color.decode("#383a40"));
        assignInputs.setBorder(new EmptyBorder(10, 10, 10, 10));
        JTextField targetUserField = new JTextField();
        targetUserField.setBorder(BorderFactory.createTitledBorder("Pseudo du membre"));
        JTextField targetRoleField = new JTextField();
        targetRoleField.setBorder(BorderFactory.createTitledBorder("Nom du rôle à donner"));
        JButton assignBtn = new JButton("Donner le rôle");
        assignBtn.setBackground(new Color(35, 165, 89));
        assignBtn.setForeground(Color.WHITE);
        assignInputs.add(targetUserField);
        assignInputs.add(targetRoleField);
        assignInputs.add(assignBtn);
        assignPanel.add(assignInputs, BorderLayout.CENTER);

        assignBtn.addActionListener(e -> {
            String tUser = targetUserField.getText().trim();
            String tRole = targetRoleField.getText().trim();
            if (!tUser.isEmpty() && !tRole.isEmpty()) {
                app.out.println("/role add " + app.activeServer + " " + tUser + " " + tRole);
                JOptionPane.showMessageDialog(roleDialog, "Commande envoyée : " + tRole + " -> " + tUser);
            }
        });

        formPanel.add(createPanel);
        formPanel.add(assignPanel);
        contentPanel.add(formPanel, BorderLayout.CENTER);
        roleDialog.add(contentPanel, BorderLayout.CENTER);
        roleDialog.setVisible(true);
    }

    public static class GlassPanel extends JPanel {
        private int radius = 15;
        private Color bgColor = GLASS_PANEL_BG;
        private Color borderColor = GLASS_BORDER;

        public GlassPanel() {
            setOpaque(false);
        }

        public GlassPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        public GlassPanel(int radius, Color bg) {
            this.radius = radius;
            this.bgColor = bg;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class GlassButton extends JButton {
        private Color hoverColor = new Color(255, 255, 255, 30);
        private boolean isHovered = false;

        public GlassButton(String text) {
            super(text);
            init();
        }

        private void init() {
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorder(new EmptyBorder(5, 15, 5, 15));
            setForeground(TEXT_HEADER);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setFont(FONT_BOLD);
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    repaint();
                }

                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (isHovered) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hoverColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    private static class ModernScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = new Color(255, 255, 255, 50);
            this.trackColor = new Color(0, 0, 0, 0);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton btn = new JButton();
            btn.setPreferredSize(new Dimension(0, 0));
            return btn;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2, thumbBounds.width - 4, thumbBounds.height - 4, 8, 8);
            g2.dispose();
        }
    }

    class PaddingRenderer extends DefaultListCellRenderer {
        private int padding;

        public PaddingRenderer(int p) {
            this.padding = p;
        }

        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(new EmptyBorder(padding / 2, padding, padding / 2, padding));
            if (isSelected) {
                label.setBackground(new Color(255, 255, 255, 30));
                label.setForeground(Color.WHITE);
                label.setOpaque(true);
            } else {
                label.setBackground(new Color(0, 0, 0, 0));
                label.setForeground(TEXT_MUTED);
                label.setOpaque(false);
            }
            return label;
        }
    }

    class DiscordMemberRenderer extends JPanel implements ListCellRenderer<String> {
        private JLabel nameLabel;
        private JPanel avatarPanel;

        public DiscordMemberRenderer() {
            setLayout(new BorderLayout(10, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(5, 10, 5, 10));
            avatarPanel = new JPanel() {
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(88, 101, 242, 200));
                    g2.fillOval(0, 0, 32, 32);
                    String u = nameLabel.getText();
                    boolean online = app.globalOnlineUsers.contains(u);
                    g2.setColor(online ? ONLINE_GREEN : Color.GRAY);
                    g2.fillOval(24, 24, 10, 10);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawOval(24, 24, 10, 10);
                }
            };
            avatarPanel.setPreferredSize(new Dimension(35, 35));
            avatarPanel.setOpaque(false);
            nameLabel = new JLabel();
            nameLabel.setFont(FONT_BOLD);
            add(avatarPanel, BorderLayout.WEST);
            add(nameLabel, BorderLayout.CENTER);
        }

        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(value);
            boolean online = app.globalOnlineUsers.contains(value);
            if (isSelected) {
                setBackground(new Color(255, 255, 255, 30));
                nameLabel.setForeground(Color.WHITE);
                setOpaque(true);
            } else {
                setBackground(new Color(0, 0, 0, 0));
                nameLabel.setForeground(online ? TEXT_HEADER : TEXT_MUTED);
                setOpaque(false);
            }
            return this;
        }
    }

    class SidebarRenderer extends JPanel implements ListCellRenderer<String> {
        private JLabel label;
        private JPanel iconPanel;
        private String currentAvatarName;
        private boolean isVoiceChannel;

        public SidebarRenderer() {
            setLayout(new BorderLayout(5, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(2, 5, 2, 5));

            iconPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    if (isVoiceChannel) {
                        // Draw Speaker Icon
                        g2.setColor(new Color(148, 155, 164));
                        // Simple Speaker Shape
                        Polygon p = new Polygon();
                        p.addPoint(2, 8);
                        p.addPoint(8, 8);
                        p.addPoint(14, 2);
                        p.addPoint(14, 22);
                        p.addPoint(8, 16);
                        p.addPoint(2, 16);
                        g2.fillPolygon(p);
                        // Waves
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.drawArc(12, 6, 8, 12, -45, 90);
                        g2.drawArc(10, 9, 4, 6, -45, 90);
                    } else if (currentAvatarName != null && !currentAvatarName.isEmpty()) {
                        // Generate color
                        int hash = currentAvatarName.hashCode();
                        Color c = new Color((hash & 0xFF0000) >> 16, (hash & 0x00FF00) >> 8, hash & 0x0000FF);
                        if (c.getRed() + c.getGreen() + c.getBlue() < 300)
                            c = c.brighter();

                        g2.setColor(c);
                        g2.fillOval(0, 0, getWidth(), getHeight());

                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                        String initial = currentAvatarName.substring(0, 1).toUpperCase();
                        FontMetrics fm = g2.getFontMetrics();
                        int x = (getWidth() - fm.stringWidth(initial)) / 2;
                        int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                        g2.drawString(initial, x, y);
                    }
                }
            };
            iconPanel.setOpaque(false);
            iconPanel.setPreferredSize(new Dimension(24, 24));
            add(iconPanel, BorderLayout.WEST);

            label = new JLabel();
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                boolean isSelected, boolean cellHasFocus) {

            setOpaque(false);
            setBackground(null);

            if (value.trim().isEmpty()) {
                currentAvatarName = null;
                isVoiceChannel = false;
                iconPanel.setVisible(false);
                label.setText("");
                return this;
            }

            if (value.startsWith("  ")) {
                // Voice User
                currentAvatarName = value.trim();
                isVoiceChannel = false;
                iconPanel.setVisible(true);
                iconPanel.setPreferredSize(new Dimension(24, 24));

                label.setText(currentAvatarName);
                label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                boolean isOnline = app.globalOnlineUsers.contains(currentAvatarName);
                label.setForeground(isOnline ? Color.WHITE : new Color(180, 184, 190));
                label.setBorder(new EmptyBorder(0, 5, 0, 0));

            } else if (value.startsWith("#")) {
                // Text Channel
                currentAvatarName = null;
                isVoiceChannel = false;
                iconPanel.setVisible(false);
                iconPanel.setPreferredSize(new Dimension(0, 0));

                label.setText(value); // Keep #
                label.setFont(new Font("Segoe UI", Font.BOLD, 15));
                label.setForeground(isSelected ? Color.WHITE : new Color(142, 146, 151));
                label.setBorder(new EmptyBorder(2, 5, 2, 0));

                if (isSelected) {
                    setOpaque(true);
                    setBackground(new Color(255, 255, 255, 20));
                    label.setForeground(Color.WHITE);
                }
            } else {
                // Default / Header / Friend
                // Check if it's a Friend (User) or Header
                // Headers usually are "Accueil" or "SERVERNAME" (but title is separate)
                // Actually "Accueil" is added to list
                if (value.equals("Accueil")) {
                    currentAvatarName = null;
                    isVoiceChannel = false;
                    iconPanel.setVisible(false);
                    label.setText(value);
                    label.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    label.setForeground(new Color(148, 155, 164));
                    label.setBorder(new EmptyBorder(5, 0, 5, 0));
                } else {
                    // Friend List Item (User)
                    currentAvatarName = value;
                    isVoiceChannel = false;
                    iconPanel.setVisible(true);
                    iconPanel.setPreferredSize(new Dimension(24, 24));

                    label.setText(value);
                    label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                    boolean isOnline = app.globalOnlineUsers.contains(value);
                    label.setForeground(isOnline ? Color.WHITE : new Color(180, 184, 190));
                    label.setBorder(new EmptyBorder(0, 5, 0, 0));
                }
            }

            return this;
        }
    }
}
