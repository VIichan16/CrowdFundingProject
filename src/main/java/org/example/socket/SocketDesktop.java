package org.example.socket;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class SocketDesktop extends JFrame {
    private static final String[] TITLES = {
            "프로젝트", "로그인", "프로젝트 등록", "프로젝트 조회", "후원하기", "좋아요",
            "리뷰 작성", "프로젝트 수정", "리워드 등록", "승인 신청", "내 후원 내역",
            "후원자 회원가입", "내 프로젝트 현황", "승인·거절", "전체 펀딩 현황", "마이페이지"
    };
    private static final String[][] FIELDS = {
            {},
            {"아이디", "비밀번호"},
            {"제목", "카테고리", "설명", "목표 금액", "시작일 (YYYY-MM-DD)", "종료일 (YYYY-MM-DD)"},
            {"프로젝트 ID 또는 검색 기준", "검색어"},
            {"프로젝트 ID", "리워드 ID", "수량"},
            {"프로젝트 ID"},
            {"프로젝트 ID", "별점 (1–5)", "리뷰 내용"},
            {"프로젝트 ID", "제목", "카테고리", "설명", "목표 금액", "시작일 (YYYY-MM-DD)", "종료일 (YYYY-MM-DD)"},
            {"프로젝트 ID"},
            {"프로젝트 ID"},
            {},
            {"아이디", "비밀번호", "이름", "전화번호"},
            {"프로젝트 ID"},
            {"프로젝트 ID", "처리", "거절 사유"},
            {"프로젝트 ID (전체 조회는 비워두기)"},
            {}
    };

    private final JPanel nav = new JPanel(), form = new JPanel(), output = new JPanel();
    private final JLabel account = new JLabel("로그인 전"), heading = new JLabel(), status = new JLabel("서버를 먼저 실행하세요.");
    private final JButton submit = new JButton("실행");
    private final ArrayList<JComponent> fields = new ArrayList<>();
    private final DefaultTableModel catalogModel = new DefaultTableModel(
            new String[]{"ID", "사진", "프로젝트", "카테고리", "상태"}, 0
    ) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable catalog = new JTable(catalogModel);
    private final TableRowSorter<DefaultTableModel> catalogSorter = new TableRowSorter<>(catalogModel);
    private final DefaultComboBoxModel<String> categoryModel = new DefaultComboBoxModel<>(new String[]{"전체 카테고리"});
    private final JComboBox<String> categoryFilter = new JComboBox<>(categoryModel);
    private final JComboBox<String> statusFilter = new JComboBox<>(
            new String[]{"전체 상태", "진행중", "성공", "대기", "종료", "실패"}
    );
    private final JTextField keywordFilter = new JTextField();
    private final JComboBox<String> sortOption = new JComboBox<>(
            new String[]{"기본순(번호순)", "번호 내림차순", "프로젝트명 가나다순", "카테고리순", "상태순"}
    );

    private final JCheckBox pending = new JCheckBox("승인 대기만");
    private final JButton approve = new JButton("선택 프로젝트 심사");
    private JSplitPane split, mainSplit;
    private JPanel work, catalogPanel, centerContent;
    private final JButton entrySwitch = new JButton("회원가입");
    private String projectImage;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private final int port;
    private int type;
    private String role = "", name = "";
    private boolean busy;

    public static void main(String[] args) {
        int port = args.length == 0 ? 9090 : Integer.parseInt(args[0]);
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            Font baseFont = new Font("맑은 고딕", Font.PLAIN, 15);
            for (String key : new String[]{
                    "Label.font", "Button.font", "ToggleButton.font", "CheckBox.font",
                    "RadioButton.font", "ComboBox.font", "TextField.font", "PasswordField.font",
                    "TextArea.font", "Table.font", "TableHeader.font", "TabbedPane.font",
                    "OptionPane.messageFont", "OptionPane.buttonFont"
            }) {
                UIManager.put(key, baseFont);
            }
            new SocketDesktop(port).setVisible(true);
        });
    }

    private SocketDesktop(int port) {
        super("크라우드펀딩 · TCP 클라이언트");
        this.port = port;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 760);
        setMinimumSize(new Dimension(720, 580));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(18, 16));
        root.setBorder(BorderFactory.createEmptyBorder(18, 22, 16, 22));
        root.setBackground(new Color(246, 248, 250));
        setContentPane(root);

        JLabel title = new JLabel("크라우드펀딩");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 27f));
        title.setForeground(new Color(28, 38, 52));

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(2, 2, 10, 2));
        bar.add(title, BorderLayout.WEST);
        account.setFont(account.getFont().deriveFont(Font.BOLD, 14f));
        account.setForeground(new Color(76, 86, 96));
        bar.add(account, BorderLayout.EAST);

        nav.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 5));
        nav.setOpaque(false);
        JPanel topArea = new JPanel(new BorderLayout(0, 8));
        topArea.setOpaque(false);
        topArea.add(bar, BorderLayout.NORTH);
        topArea.add(nav, BorderLayout.CENTER);
        root.add(topArea, BorderLayout.NORTH);

        work = new JPanel(new BorderLayout(0, 10));
        work.setOpaque(false);
        work.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        JPanel head = new JPanel(new BorderLayout());
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 19f));
        head.add(heading);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.add(entrySwitch);
        buttons.add(submit);
        head.add(buttons, BorderLayout.EAST);

        entrySwitch.addActionListener(e -> {
            if (busy) return;
            if (type == 11) showLoginDialog();
            else page(11);
        });

        work.add(head, BorderLayout.NORTH);

        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        output.setLayout(new BoxLayout(output, BoxLayout.Y_AXIS));

        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(form), new JScrollPane(output));
        split.setResizeWeight(.34);
        split.setDividerLocation(205);
        split.setDividerSize(0);
        split.setEnabled(false);

        work.add(split, BorderLayout.CENTER);
        status.setForeground(new Color(105, 115, 125));
        status.setFont(status.getFont().deriveFont(12f));
        work.add(status, BorderLayout.SOUTH);

        catalogPanel = new JPanel(new BorderLayout(0, 10));
        catalogPanel.setPreferredSize(new Dimension(610, 0));
        catalogPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 230, 235)),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        catalogPanel.setBackground(Color.WHITE);

        JButton refreshBtn = new JButton("새로고침");
        JButton detail = new JButton("상세·리워드 사진");

        JPanel actions = new JPanel(new GridLayout(1, 0, 4, 4));
        actions.add(detail);
        actions.add(pending);
        actions.add(approve);

        JPanel filterBar = new JPanel(new GridLayout(2, 2, 4, 4));
        filterBar.add(categoryFilter);
        filterBar.add(statusFilter);
        filterBar.add(keywordFilter);
        filterBar.add(sortOption);

        JPanel filterArea = new JPanel(new BorderLayout(8, 0));
        filterArea.add(filterBar, BorderLayout.CENTER);
        filterArea.add(refreshBtn, BorderLayout.EAST);

        JPanel catalogTop = new JPanel(new BorderLayout(0, 4));
        catalogTop.add(actions, BorderLayout.NORTH);
        catalogTop.add(filterArea, BorderLayout.SOUTH);

        catalogPanel.add(catalogTop, BorderLayout.NORTH);
        JScrollPane catalogScroll = new JScrollPane(catalog);
        catalogScroll.setBorder(BorderFactory.createLineBorder(new Color(232, 236, 240)));
        catalogPanel.add(catalogScroll, BorderLayout.CENTER);

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, catalogPanel, work);
        mainSplit.setResizeWeight(.52);
        mainSplit.setDividerLocation(0.52);
        mainSplit.setDividerSize(0);
        mainSplit.setEnabled(false);
        SwingUtilities.invokeLater(() -> mainSplit.setDividerLocation(0.52));
        mainSplit.setBorder(null);
        mainSplit.setOpaque(false);
        centerContent = new JPanel(new BorderLayout());
        centerContent.setOpaque(false);
        centerContent.add(mainSplit, BorderLayout.CENTER);
        root.add(centerContent, BorderLayout.CENTER);

        catalog.setRowSorter(catalogSorter);
        catalogSorter.setComparator(0, Comparator.comparingInt(v -> {
            try {
                return Integer.parseInt(String.valueOf(v).trim());
            } catch (Exception ex) {
                return 0;
            }
        }));

        catalog.setRowHeight(38);
        catalog.setShowVerticalLines(false);
        catalog.setIntercellSpacing(new Dimension(0, 1));
        catalog.setFillsViewportHeight(true);
        catalog.setSelectionBackground(new Color(221, 239, 233));
        catalog.setSelectionForeground(new Color(18, 55, 43));
        catalog.getTableHeader().setPreferredSize(new Dimension(0, 34));
        catalog.setFont(catalog.getFont().deriveFont(15f));
        catalog.getTableHeader().setFont(catalog.getTableHeader().getFont().deriveFont(Font.BOLD, 15f));
        catalog.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        catalog.setRowHeight(112);
        catalog.getColumnModel().getColumn(0).setMinWidth(0);
        catalog.getColumnModel().getColumn(0).setMaxWidth(0);
        catalog.getColumnModel().getColumn(0).setWidth(0);
        catalog.getColumnModel().getColumn(1).setPreferredWidth(140);
        catalog.getColumnModel().getColumn(2).setPreferredWidth(390);
        catalog.getColumnModel().getColumn(3).setPreferredWidth(150);
        catalog.getColumnModel().getColumn(4).setPreferredWidth(110);
        catalog.getColumn("사진").setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(
                        table, value == null ? "사진 없음" : "", selected, focused, row, column);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setIcon(value instanceof ImageIcon icon ? icon : null);
                return label;
            }
        });

        ActionListener filterAction = e -> applyCatalogFilterAndSort();
        categoryFilter.addActionListener(filterAction);
        statusFilter.addActionListener(filterAction);
        sortOption.addActionListener(filterAction);
        keywordFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyCatalogFilterAndSort(); }
            @Override public void removeUpdate(DocumentEvent e) { applyCatalogFilterAndSort(); }
            @Override public void changedUpdate(DocumentEvent e) { applyCatalogFilterAndSort(); }
        });

        refreshBtn.addActionListener(e -> refresh());
        pending.addActionListener(e -> refresh());
        detail.addActionListener(e -> openProject());

        approve.addActionListener(e -> {
            String id = selected();
            if (id == null || busy) return;
            page(13);
            ((JTextField) fields.get(0)).setText(id);
        });

        catalog.getSelectionModel().addListSelectionListener(e -> {
        });

        catalog.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) openProject();
            }
        });

        submit.addActionListener(e -> send());
        getRootPane().setDefaultButton(submit);

        menu();
        page(0);
        refresh();
        applyCatalogFilterAndSort();
    }

    private void syncCategoryFilterOptions() {
        String current = String.valueOf(categoryFilter.getSelectedItem());
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        categories.add("전체 카테고리");
        for (int r = 0; r < catalogModel.getRowCount(); r++) {
            String cat = String.valueOf(catalogModel.getValueAt(r, 3)).trim();
            if (!cat.isEmpty()) categories.add(cat);
        }
        categoryModel.removeAllElements();
        for (String cat : categories) {
            categoryModel.addElement(cat);
        }
        if (categories.contains(current)) {
            categoryFilter.setSelectedItem(current);
        } else {
            categoryFilter.setSelectedIndex(0);
        }
    }

    private void applyCatalogFilterAndSort() {
        String selectedCategory = String.valueOf(categoryFilter.getSelectedItem());
        String selectedStatus = String.valueOf(statusFilter.getSelectedItem());
        String keyword = keywordFilter.getText().trim().toLowerCase(Locale.ROOT);

        catalogSorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                String title = entry.getStringValue(2).toLowerCase(Locale.ROOT);
                String category = entry.getStringValue(3).trim();
                String statusText = entry.getStringValue(4).trim();

                if (!"전체 카테고리".equals(selectedCategory) && !selectedCategory.equals(category)) {
                    return false;
                }
                if (!"전체 상태".equals(selectedStatus) && !selectedStatus.equals(statusText)) {
                    return false;
                }
                if (!keyword.isEmpty() && !title.contains(keyword)) {
                    return false;
                }
                return true;
            }
        });

        int sortIdx = sortOption.getSelectedIndex();
        List<RowSorter.SortKey> keys = new ArrayList<>();
        switch (sortIdx) {
            case 1 -> keys.add(new RowSorter.SortKey(0, SortOrder.DESCENDING));
            case 2 -> keys.add(new RowSorter.SortKey(1, SortOrder.ASCENDING));
            case 3 -> keys.add(new RowSorter.SortKey(3, SortOrder.ASCENDING));
            case 4 -> keys.add(new RowSorter.SortKey(4, SortOrder.ASCENDING));
            default -> keys.add(new RowSorter.SortKey(0, SortOrder.ASCENDING));
        }
        catalogSorter.setSortKeys(keys);
        catalogSorter.sort();
    }

    private void menu() {
        pending.setVisible(role.equals("ADMIN"));
        approve.setVisible(role.equals("ADMIN"));
        pending.setSelected(role.equals("ADMIN"));
        nav.removeAll();

        account.setText(switch (role) {
            case "MAKER" -> "메이커 · " + name;
            case "SUPPORTER" -> "서포터 · " + name;
            case "ADMIN" -> "관리자 · " + name;
            default -> "로그인 전";
        });

        int[] menu = switch (role) {
            case "MAKER" -> new int[]{0, 12, 2, 7, 8, 9, 15};
            case "SUPPORTER" -> new int[]{0, 15};
            case "ADMIN" -> new int[]{0, 14, 13, 15};
            default -> new int[]{1, 11};
        };

        for (int t : menu) button(TITLES[t], () -> {
            if (t == 1) showLoginDialog();
            else if (t == 15) openMyPageDialog();
            else page(t);
        });

        if (!role.isEmpty()) {
            // ▼▼▼ [메이커 로그아웃 버튼 부제 추가 시작] ▼▼▼
            String logoutSubtitle = "MAKER".equals(role) ? "메이커 계정" : null;
            button("로그아웃", logoutSubtitle, () -> {
                disconnect();
                role = "";
                name = "";
                menu();
                page(1);
                refresh();
            });
            // ▲▲▲ [메이커 로그아웃 버튼 부제 추가 끝] ▲▲▲
        }

        nav.revalidate();
        nav.repaint();
    }

    private void button(String title, Runnable action) {
        button(title, null, action);
    }

    // ▼▼▼ [메이커 로그아웃 버튼 부제 지원 button 오버로드 시작] ▼▼▼
    private void button(String title, String subtitle, Runnable action) {
        JButton b = new JButton();
        b.setMaximumSize(new Dimension(170, subtitle == null ? 42 : 52));
        b.setPreferredSize(new Dimension(152, subtitle == null ? 40 : 50));
        b.setFont(b.getFont().deriveFont(Font.BOLD, 14f));
        b.setFocusPainted(false);
        b.setBackground(Color.WHITE);
        b.setForeground(new Color(47, 58, 70));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 228, 234)),
                BorderFactory.createEmptyBorder(5, 13, 5, 13)));

        if (subtitle == null || subtitle.isBlank()) {
            b.setText(title);
        } else {
            b.setText("<html><div style='text-align:center;line-height:1.15;'>"
                    + escapeHtml(title)
                    + "<br><span style='font-size:10px;font-weight:normal;color:#6C7A89;'>"
                    + escapeHtml(subtitle) + "</span></div></html>");
        }

        b.addActionListener(e -> {
            if (!busy) action.run();
        });
        nav.add(b);
    }
    // ▲▲▲ [메이커 로그아웃 버튼 부제 지원 button 오버로드 끝] ▲▲▲

    private void showLoginDialog() {
        if (busy) return;

        JDialog dialog = new JDialog(this, "로그인", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(430, 300);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(14, 18));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JLabel title = new JLabel("로그인", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        panel.add(title, BorderLayout.NORTH);

        JPanel fieldsPanel = new JPanel(new GridLayout(2, 2, 12, 14));
        JTextField loginId = new JTextField();
        JPasswordField password = new JPasswordField();
        fieldsPanel.add(new JLabel("아이디"));
        fieldsPanel.add(loginId);
        fieldsPanel.add(new JLabel("비밀번호"));
        fieldsPanel.add(password);
        panel.add(fieldsPanel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
        JButton login = new JButton("로그인");
        JButton cancel = new JButton("취소");
        buttons.add(login);
        buttons.add(cancel);
        panel.add(buttons, BorderLayout.SOUTH);

        login.addActionListener(e -> {
            if (busy) return;
            String id = loginId.getText().strip();
            String pw = new String(password.getPassword());
            if (id.isBlank() || pw.isBlank()) {
                JOptionPane.showMessageDialog(dialog, "아이디와 비밀번호를 입력하세요.");
                return;
            }
            login.setEnabled(false);
            request(1, id + "\t" + pw, result -> {
                login.setEnabled(true);
                if (result.code() != Packet.SUCCESS) {
                    JOptionPane.showMessageDialog(dialog, result.body(), "로그인 실패", JOptionPane.WARNING_MESSAGE);
                    password.setText("");
                    return;
                }
                String[] values = result.body().split("\t", 2);
                role = values.length > 0 ? values[0] : "";
                name = values.length > 1 ? values[1] : "";
                menu();
                page(role.equals("ADMIN") ? 14 : role.equals("MAKER") ? 12 : 0);
                dialog.dispose();
                refresh();
            });
        });
        cancel.addActionListener(e -> dialog.dispose());
        dialog.getRootPane().setDefaultButton(login);
        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }

    private void row(String label, JComponent component) {
        JPanel r = new JPanel(new BorderLayout(10, 0));
        r.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(role.isEmpty() ? 90 : 155, 26));

        r.add(l, BorderLayout.WEST);
        r.add(component);
        int rowHeight = "projectImagePreview".equals(component.getName()) ? 190 : 42;
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));
        r.setPreferredSize(new Dimension(0, rowHeight));
        form.add(r);
    }

    private void field(String label, JComponent c) {
        fields.add(c);
        row(label, c);
    }

    private void reward() {
        form.add(new JSeparator());

        for (String label : new String[]{"리워드명", "설명", "필요 금액", "수량 (무제한은 0)"}) {
            field(label, new JTextField());
        }

        JTextField encoded = new JTextField();
        fields.add(encoded);

        JButton choose = new JButton("사진 선택 (JPG/PNG)");

        choose.addActionListener(e -> {
            if (busy) return;

            JFileChooser picker = new JFileChooser();
            picker.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JPG / PNG", "jpg", "jpeg", "png"));

            if (picker.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

            try (var input = java.nio.file.Files.newInputStream(picker.getSelectedFile().toPath())) {
                byte[] bytes = input.readNBytes(2 * 1024 * 1024 + 1);

                if (bytes.length > 2 * 1024 * 1024) {
                    throw new IOException("리워드 사진은 2 MiB 이하로 선택하세요.");
                }

                if (bytes.length == 0 || new ImageIcon(bytes).getIconWidth() <= 0) {
                    throw new IOException("사진을 읽을 수 없습니다.");
                }

                encoded.setText(java.util.Base64.getEncoder().encodeToString(bytes));
                choose.setText(picker.getSelectedFile().getName());

            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "사진 선택 실패", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel photo = new JPanel(new BorderLayout());
        photo.add(choose);

        JButton clear = new JButton("제거");
        clear.addActionListener(e -> {
            if (!busy) {
                encoded.setText("");
                choose.setText("사진 선택 (JPG/PNG)");
            }
        });

        photo.add(clear, BorderLayout.EAST);
        row("리워드 사진 (선택)", photo);
        form.revalidate();
        form.repaint();
    }

    private record MyPageMenu(String title, JComponent view, Runnable onShow) {}

    private void openMyPageDialog() {
        if (role.isEmpty()) {
            JOptionPane.showMessageDialog(this, "로그인 후 이용할 수 있습니다.", "마이페이지", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "마이페이지", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(980, 680);
        dialog.setMinimumSize(new Dimension(820, 560));
        dialog.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        dialog.setContentPane(root);

        JPanel header = new JPanel(new BorderLayout());
        JPanel headingBox = new JPanel();
        headingBox.setLayout(new BoxLayout(headingBox, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("마이페이지");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        JLabel subtitle = new JLabel(name + "님, 회원 정보를 확인하고 관리할 수 있습니다.");
        subtitle.setForeground(new Color(105, 105, 105));
        headingBox.add(title);
        headingBox.add(Box.createVerticalStrut(6));
        headingBox.add(subtitle);
        header.add(headingBox, BorderLayout.WEST);
        JButton exit = new JButton("나가기");
        exit.addActionListener(e -> dialog.dispose());
        header.add(exit, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        CardLayout infoCards = new CardLayout();
        JPanel infoPanel = new JPanel(infoCards);

        String[] infoLabels = {"아이디", "이름", "역할", "전화번호", "자기소개"};
        JLabel[] infoValues = new JLabel[infoLabels.length];
        JPanel infoView = new JPanel(new BorderLayout(10, 10));
        infoView.setBorder(BorderFactory.createEmptyBorder(6, 12, 12, 12));
        infoView.add(myPageSectionTitle("내 정보"), BorderLayout.NORTH);
        JPanel infoGrid = new JPanel(new GridBagLayout());
        GridBagConstraints ig = new GridBagConstraints();
        ig.insets = new Insets(8, 8, 8, 8);
        ig.anchor = GridBagConstraints.WEST;
        ig.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < infoLabels.length; i++) {
            ig.gridx = 0; ig.gridy = i; ig.weightx = 0;
            JLabel label = new JLabel(infoLabels[i]);
            label.setForeground(new Color(105, 105, 105));
            label.setPreferredSize(new Dimension(145, 32));
            infoGrid.add(label, ig);
            ig.gridx = 1; ig.weightx = 1;
            infoValues[i] = new JLabel("-");
            infoValues[i].setFont(infoValues[i].getFont().deriveFont(Font.PLAIN, 15f));
            infoGrid.add(infoValues[i], ig);
        }
        ig.gridx = 0; ig.gridy = infoLabels.length; ig.gridwidth = 2; ig.weighty = 1;
        infoGrid.add(Box.createGlue(), ig);
        infoView.add(new JScrollPane(infoGrid), BorderLayout.CENTER);
        JLabel infoStatus = new JLabel(" ");
        JButton editInfo = new JButton("개인 정보 수정");
        editInfo.setPreferredSize(new Dimension(150, 40));
        editInfo.setEnabled(false);
        JPanel infoBottom = new JPanel(new BorderLayout(8, 8));
        infoBottom.add(infoStatus, BorderLayout.CENTER);
        infoBottom.add(editInfo, BorderLayout.EAST);
        infoView.add(infoBottom, BorderLayout.SOUTH);

        JPanel editView = new JPanel(new BorderLayout(10, 10));
        editView.setBorder(BorderFactory.createEmptyBorder(6, 12, 12, 12));
        editView.add(myPageSectionTitle("개인 정보 수정"), BorderLayout.NORTH);
        JPanel profileForm = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;
        JTextField userId = new JTextField(); userId.setEditable(false);
        JTextField userName = new JTextField();
        JTextField phone = new JTextField();
        JTextField bio = new JTextField();
        JPasswordField currentPw = new JPasswordField();
        JPasswordField newPw = new JPasswordField();
        JPasswordField confirmPw = new JPasswordField();
        JComponent[] inputs = {userId, userName, phone, bio, currentPw, newPw, confirmPw};
        String[] labels = {"아이디", "이름", "전화번호", "자기소개", "현재 비밀번호", "새 비밀번호", "새 비밀번호 확인"};
        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0; gbc.gridy = i; gbc.weightx = 0;
            JLabel label = new JLabel(labels[i]);
            label.setPreferredSize(new Dimension(145, 32));
            profileForm.add(label, gbc);
            gbc.gridx = 1; gbc.weightx = 1;
            inputs[i].setPreferredSize(new Dimension(300, 36));
            profileForm.add(inputs[i], gbc);
        }
        editView.add(new JScrollPane(profileForm), BorderLayout.CENTER);
        JLabel passwordHint = new JLabel("비밀번호를 변경하지 않으려면 비밀번호 입력란을 비워 두세요.");
        passwordHint.setForeground(new Color(110, 110, 110));
        JButton cancelEdit = new JButton("취소");
        cancelEdit.setPreferredSize(new Dimension(100, 40));
        JButton confirmEdit = new JButton("확인");
        confirmEdit.setPreferredSize(new Dimension(100, 40));
        JPanel editButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        editButtons.add(cancelEdit);
        editButtons.add(confirmEdit);
        JPanel editBottom = new JPanel(new BorderLayout(8, 8));
        editBottom.add(passwordHint, BorderLayout.CENTER);
        editBottom.add(editButtons, BorderLayout.EAST);
        editView.add(editBottom, BorderLayout.SOUTH);

        infoPanel.add(infoView, "view");
        infoPanel.add(editView, "edit");

        String[][] profile = {null};
        Runnable clearPasswords = () -> { currentPw.setText(""); newPw.setText(""); confirmPw.setText(""); };
        Runnable loadInfo = () -> {
            infoStatus.setText("회원 정보를 불러오는 중입니다...");
            whenIdle(() -> request(15, "", result -> {
                if (result.code() != Packet.SUCCESS) {
                    infoStatus.setText("회원정보 조회 실패: " + result.body());
                    return;
                }
                String[] lines = result.body().split("\n", -1);
                String[] data = lines.length >= 2 ? lines[1].split("\t", -1) : new String[0];
                if (data.length < 6) {
                    infoStatus.setText("회원정보를 찾을 수 없습니다.");
                    return;
                }
                for (int i = 0; i < data.length; i++) data[i] = unescapeCell(data[i]);
                if (data[5].equals("null")) data[5] = "";
                profile[0] = data;
                infoValues[0].setText(data[1]);
                infoValues[1].setText(data[2]);
                infoValues[2].setText(roleName(data[3]));
                infoValues[3].setText(data[4]);
                infoValues[4].setText(data[5].isEmpty() ? "-" : data[5]);
                infoStatus.setText(" ");
                editInfo.setEnabled(true);
            }), () -> infoStatus.setText("다른 요청을 처리 중입니다. 잠시 후 [내 정보]를 다시 눌러 주세요."));
        };

        editInfo.addActionListener(e -> {
            String[] data = profile[0];
            if (data == null) return;
            userId.setText(data[1]);
            userName.setText(data[2]);
            phone.setText(data[4]);
            bio.setText(data[5]);
            clearPasswords.run();
            infoCards.show(infoPanel, "edit");
        });
        cancelEdit.addActionListener(e -> {
            clearPasswords.run();
            infoCards.show(infoPanel, "view");
        });
        confirmEdit.addActionListener(e -> {
            if (busy) {
                JOptionPane.showMessageDialog(dialog, "다른 요청을 처리 중입니다. 잠시 후 다시 눌러 주세요.");
                return;
            }
            String current = new String(currentPw.getPassword());
            String next = new String(newPw.getPassword());
            String confirm = new String(confirmPw.getPassword());
            if ((!current.isEmpty() || !next.isEmpty() || !confirm.isEmpty()) &&
                    (current.isBlank() || next.isBlank() || confirm.isBlank())) {
                JOptionPane.showMessageDialog(dialog, "비밀번호 변경 시 현재 비밀번호와 새 비밀번호, 확인을 모두 입력하세요.");
                return;
            }
            if (!next.isEmpty() && (next.length() < 8 || next.length() > 72)) {
                JOptionPane.showMessageDialog(dialog, "새 비밀번호는 8~72자로 입력하세요.");
                return;
            }
            if (!next.equals(confirm)) {
                JOptionPane.showMessageDialog(dialog, "새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
                return;
            }
            String[] vals = {userName.getText().strip(), phone.getText().strip(), bio.getText().strip(), current, next, confirm};
            for (String v : vals) {
                if (v.contains("\t") || v.contains("\n") || v.contains("\r")) {
                    JOptionPane.showMessageDialog(dialog, "입력값에 탭이나 줄바꿈을 사용할 수 없습니다.");
                    return;
                }
            }
            request(19, String.join("\t", vals), result -> {
                if (result.code() == Packet.SUCCESS) {
                    name = vals[0];
                    account.setText(switch (role) {
                        case "MAKER" -> "메이커 · " + name;
                        case "SUPPORTER" -> "서포터 · " + name;
                        case "ADMIN" -> "관리자 · " + name;
                        default -> name;
                    });
                    subtitle.setText(name + "님, 회원 정보를 확인하고 관리할 수 있습니다.");
                    clearPasswords.run();
                    infoCards.show(infoPanel, "view");
                    JOptionPane.showMessageDialog(dialog, "개인정보가 수정되었습니다.", "저장 완료", JOptionPane.INFORMATION_MESSAGE);
                    loadInfo.run();
                } else {
                    JOptionPane.showMessageDialog(dialog, result.body(), "수정 실패", JOptionPane.ERROR_MESSAGE);
                }
            });
        });

        JPanel historyPanel = new JPanel(new BorderLayout(10, 10));
        historyPanel.setBorder(BorderFactory.createEmptyBorder(6, 12, 12, 12));
        historyPanel.add(myPageSectionTitle("내가 후원한 프로젝트"), BorderLayout.NORTH);
        DefaultTableModel historyModel = new DefaultTableModel(new Object[]{"후원 내역"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable historyTable = myPageTable(historyModel);
        historyPanel.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        JButton writeReview = new JButton("선택한 프로젝트 리뷰 작성");
        writeReview.setEnabled(false);
        historyTable.getSelectionModel().addListSelectionListener(e ->
                writeReview.setEnabled(!e.getValueIsAdjusting() && historyTable.getSelectedRow() >= 0));
        writeReview.addActionListener(e -> {
            int selectedViewRow = historyTable.getSelectedRow();
            if (selectedViewRow < 0) return;
            int selectedModelRow = historyTable.convertRowIndexToModel(selectedViewRow);
            int idColumn = historyModel.findColumn("project_id");
            if (idColumn < 0) return;
            String projectId = String.valueOf(historyModel.getValueAt(selectedModelRow, idColumn));
            openReviewDialog(dialog, projectId);
        });
        JPanel historyBottom = new JPanel(new BorderLayout(8, 8));
        JLabel historyStatus = new JLabel(" ");
        historyBottom.add(historyStatus, BorderLayout.CENTER);
        JButton cancelFunding = new JButton("후원 취소");
        cancelFunding.setEnabled(false);
        historyTable.getSelectionModel().addListSelectionListener(e -> {
            int row = historyTable.getSelectedRow();
            cancelFunding.setEnabled(row >= 0 && historyModel.findColumn("funding_id") >= 0
                    && "후원완료".equals(String.valueOf(historyModel.getValueAt(historyTable.convertRowIndexToModel(row), 5))));
        });
        JPanel historyActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        historyActions.add(cancelFunding);
        historyActions.add(writeReview);
        historyBottom.add(historyActions, BorderLayout.EAST);
        historyPanel.add(historyBottom, BorderLayout.SOUTH);

        Runnable loadHistory = () -> {
            historyStatus.setText("후원 내역을 불러오는 중입니다...");
            whenIdle(() -> request(10, "", history -> {
                historyModel.setRowCount(0);
                writeReview.setEnabled(false);
                cancelFunding.setEnabled(false);
                if (history.code() != Packet.SUCCESS) {
                    historyStatus.setText("후원 내역 조회 실패: " + history.body());
                    return;
                }
                String body = history.body();
                if (body == null || body.isBlank() || body.startsWith("후원 내역은 후원자 계정")) {
                    historyStatus.setText(body == null || body.isBlank() ? "후원 내역이 없습니다." : body);
                    return;
                }
                String[] rows = body.split("\n", -1);
                historyModel.setColumnIdentifiers(rows[0].split("\t", -1));
                for (int i = 1; i < rows.length; i++) {
                    if (rows[i].isBlank()) continue;
                    String[] cells = rows[i].split("\t", -1);
                    for (int c = 0; c < cells.length; c++) cells[c] = unescapeCell(cells[c]);
                    if (cells.length > 3) {
                        try {
                            java.math.BigDecimal amount = new java.math.BigDecimal(cells[3]);
                            cells[3] = String.format(Locale.KOREA, "%,.0f원", amount);
                        } catch (NumberFormatException ignored) { }
                    }
                    historyModel.addRow(cells);
                }
                if (historyTable.getColumnModel().getColumnCount() == 8) {
                    historyTable.removeColumn(historyTable.getColumnModel().getColumn(7));
                    historyTable.removeColumn(historyTable.getColumnModel().getColumn(6));
                }
                if (historyTable.getColumnModel().getColumnCount() == 6) {
                    int[] widths = {280, 220, 85, 145, 170, 100};
                    for (int i = 0; i < widths.length; i++)
                        historyTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
                }
                historyStatus.setText(historyModel.getRowCount() == 0 ? "후원 내역이 없습니다." : "총 " + historyModel.getRowCount() + "건의 후원 내역");
            }), () -> historyStatus.setText("다른 요청을 처리 중입니다. 잠시 후 [내 후원 내역]을 다시 눌러 주세요."));
        };

        cancelFunding.addActionListener(e -> {
            if (busy) {
                JOptionPane.showMessageDialog(dialog, "다른 요청 처리 중입니다. 잠시 후 다시 시도하세요.");
                return;
            }
            int row = historyTable.getSelectedRow();
            int idColumn = historyModel.findColumn("funding_id");
            if (row < 0 || idColumn < 0) return;
            String fundingId = String.valueOf(historyModel.getValueAt(historyTable.convertRowIndexToModel(row), idColumn));
            if (JOptionPane.showConfirmDialog(dialog, "선택한 후원을 취소하시겠습니까?", "후원 취소",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            if (busy || !dialog.isDisplayable() || !"SUPPORTER".equals(role)) return;
            cancelFunding.setEnabled(false);
            request(22, fundingId, result -> {
                JOptionPane.showMessageDialog(dialog, result.body(), "후원 취소",
                        result.code() == Packet.SUCCESS ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                if (dialog.isDisplayable()) loadHistory.run();
            });
        });

        JPanel likePanel = new JPanel(new BorderLayout(10, 10));
        likePanel.setBorder(BorderFactory.createEmptyBorder(6, 12, 12, 12));
        likePanel.add(myPageSectionTitle("♥ 좋아요한 프로젝트"), BorderLayout.NORTH);
        DefaultTableModel likeModel = new DefaultTableModel(new Object[]{"프로젝트명", "카테고리", "상태", "달성률", "좋아요 날짜", "ID"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable likeTable = myPageTable(likeModel);
        likeTable.removeColumn(likeTable.getColumnModel().getColumn(5));
        int[] likeWidths = {300, 140, 100, 110, 140};
        for (int i = 0; i < likeWidths.length; i++)
            likeTable.getColumnModel().getColumn(i).setPreferredWidth(likeWidths[i]);
        likePanel.add(new JScrollPane(likeTable), BorderLayout.CENTER);
        JLabel likeStatus = new JLabel(" ");
        JButton likeDetail = new JButton("상세 보기");
        JButton likeCancel = new JButton("♡ 좋아요 취소");
        likeDetail.setEnabled(false);
        likeCancel.setEnabled(false);
        likeTable.getSelectionModel().addListSelectionListener(e -> {
            boolean picked = likeTable.getSelectedRow() >= 0;
            likeDetail.setEnabled(picked);
            likeCancel.setEnabled(picked);
        });
        JPanel likeButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        likeButtons.add(likeDetail);
        likeButtons.add(likeCancel);
        JPanel likeBottom = new JPanel(new BorderLayout(8, 8));
        likeBottom.add(likeStatus, BorderLayout.CENTER);
        likeBottom.add(likeButtons, BorderLayout.EAST);
        likePanel.add(likeBottom, BorderLayout.SOUTH);

        Runnable loadLikes = () -> {
            likeStatus.setText("좋아요한 프로젝트를 불러오는 중입니다...");
            whenIdle(() -> request(20, "", result -> {
                likeModel.setRowCount(0);
                if (result.code() != Packet.SUCCESS) {
                    likeStatus.setText("좋아요 목록 조회 실패: " + result.body());
                    return;
                }
                String[] rows = result.body().split("\n", -1);
                for (int i = 1; i < rows.length; i++) {
                    if (rows[i].isBlank()) continue;
                    String[] cells = rows[i].split("\t", -1);
                    if (cells.length < 6) continue;
                    for (int c = 0; c < cells.length; c++) cells[c] = unescapeCell(cells[c]);
                    if (!cells[3].isEmpty()) cells[3] = cells[3] + "%";
                    likeModel.addRow(new Object[]{cells[0], cells[1], cells[2], cells[3], cells[4], cells[5]});
                }
                likeStatus.setText(likeModel.getRowCount() == 0 ? "좋아요한 프로젝트가 없습니다." : "총 " + likeModel.getRowCount() + "개의 좋아요한 프로젝트");
            }), () -> likeStatus.setText("다른 요청을 처리 중입니다. 잠시 후 [좋아요한 프로젝트]를 다시 눌러 주세요."));
        };

        java.util.function.Supplier<String[]> selectedLike = () -> {
            int viewRow = likeTable.getSelectedRow();
            if (viewRow < 0) return null;
            int modelRow = likeTable.convertRowIndexToModel(viewRow);
            return new String[]{String.valueOf(likeModel.getValueAt(modelRow, 5)), String.valueOf(likeModel.getValueAt(modelRow, 0))};
        };
        Runnable openLikedProject = () -> {
            String[] picked = selectedLike.get();
            if (picked == null) return;
            if (busy) { likeStatus.setText("다른 요청을 처리 중입니다. 잠시 후 다시 눌러 주세요."); return; }
            openProject(picked[0], picked[1], dialog);
        };
        likeDetail.addActionListener(e -> openLikedProject.run());
        likeTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) openLikedProject.run();
            }
        });
        likeCancel.addActionListener(e -> {
            String[] picked = selectedLike.get();
            if (picked == null) return;
            int answer = JOptionPane.showConfirmDialog(dialog, "「" + picked[1] + "」 좋아요를 취소할까요?", "좋아요 취소", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) return;
            if (busy) { likeStatus.setText("다른 요청을 처리 중입니다. 잠시 후 다시 눌러 주세요."); return; }
            request(21, picked[0], result -> {
                if (result.code() == Packet.SUCCESS) loadLikes.run();
                else JOptionPane.showMessageDialog(dialog, result.body(), "좋아요 취소", JOptionPane.INFORMATION_MESSAGE);
            });
        });

        List<MyPageMenu> menus = new ArrayList<>();
        menus.add(new MyPageMenu("내 정보", infoPanel, () -> { infoCards.show(infoPanel, "view"); clearPasswords.run(); loadInfo.run(); }));
        if (role.equals("SUPPORTER")) {
            menus.add(new MyPageMenu("내 후원 내역", historyPanel, loadHistory));
            menus.add(new MyPageMenu("좋아요한 프로젝트", likePanel, loadLikes));
        }

        CardLayout contentCards = new CardLayout();
        JPanel content = new JPanel(contentCards);
        content.setBorder(BorderFactory.createLineBorder(new Color(210, 214, 220)));
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        ButtonGroup sideGroup = new ButtonGroup();
        List<JToggleButton> sideButtons = new ArrayList<>();
        for (MyPageMenu menu : menus) {
            content.add(menu.view(), menu.title());
            JToggleButton button = new JToggleButton(menu.title());
            button.setFont(button.getFont().deriveFont(Font.PLAIN, 15f));
            button.setFocusPainted(false);
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setPreferredSize(new Dimension(170, 48));
            button.setMaximumSize(new Dimension(170, 48));
            button.addActionListener(e -> {
                contentCards.show(content, menu.title());
                menu.onShow().run();
            });
            sideGroup.add(button);
            sideButtons.add(button);
            side.add(button);
            side.add(Box.createVerticalStrut(10));
        }
        JPanel sideWrap = new JPanel(new BorderLayout());
        sideWrap.add(side, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(16, 0));
        body.add(sideWrap, BorderLayout.WEST);
        body.add(content, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);

        dialog.setVisible(true);

        sideButtons.get(0).setSelected(true);
        contentCards.show(content, menus.get(0).title());
        menus.get(0).onShow().run();
    }

    private void whenIdle(Runnable action, Runnable gaveUp) {
        whenIdle(action, gaveUp, 25);
    }

    private void whenIdle(Runnable action, Runnable gaveUp, int triesLeft) {
        if (!busy) { action.run(); return; }
        if (triesLeft <= 0) { gaveUp.run(); return; }
        Timer retry = new Timer(200, e -> whenIdle(action, gaveUp, triesLeft - 1));
        retry.setRepeats(false);
        retry.start();
    }

    private JLabel myPageSectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 19f));
        return label;
    }

    private JTable myPageTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(42);
        table.setFont(table.getFont().deriveFont(Font.PLAIN, 15f));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 15f));
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setGridColor(new Color(230, 233, 238));
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        return table;
    }

    private static String roleName(String role) {
        return switch (role) {
            case "SUPPORTER" -> "서포터";
            case "MAKER" -> "메이커";
            case "ADMIN" -> "관리자";
            default -> role;
        };
    }

    private static String unescapeCell(String value) {
        if (value.indexOf('\\') < 0) return value;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                out.append(switch (next) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    default -> next;
                });
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private void openReviewDialog(JDialog owner, String projectId) {
        if (!"SUPPORTER".equals(role)) {
            JOptionPane.showMessageDialog(owner, "서포터 계정에서만 리뷰를 작성할 수 있습니다.");
            return;
        }
        JDialog reviewDialog = new JDialog(owner, "프로젝트 리뷰 작성", Dialog.ModalityType.APPLICATION_MODAL);
        reviewDialog.setSize(480, 330);
        reviewDialog.setLocationRelativeTo(owner);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        JPanel top = new JPanel(new GridLayout(2, 2, 8, 8));
        top.add(new JLabel("프로젝트 번호"));
        top.add(new JLabel(projectId));
        top.add(new JLabel("별점"));
        JComboBox<Integer> rating = new JComboBox<>(new Integer[]{5, 4, 3, 2, 1});
        top.add(rating);
        JTextArea content = new JTextArea(6, 30);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        JScrollPane contentScroll = new JScrollPane(content);
        contentScroll.setBorder(BorderFactory.createTitledBorder("리뷰 내용"));
        JButton save = new JButton("리뷰 등록");
        JButton cancel = new JButton("취소");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel); buttons.add(save);
        cancel.addActionListener(e -> reviewDialog.dispose());
        save.addActionListener(e -> {
            String reviewText = content.getText().strip();
            if (reviewText.isEmpty()) {
                JOptionPane.showMessageDialog(reviewDialog, "리뷰 내용을 입력하세요.");
                return;
            }
            if (reviewText.contains("\t") || reviewText.contains("\n") || reviewText.contains("\r")) {
                reviewText = reviewText.replace("\r", " ").replace("\n", " ").replace("\t", " ");
            }
            save.setEnabled(false);
            request(6, projectId + "\t" + rating.getSelectedItem() + "\t" + reviewText, result -> {
                save.setEnabled(true);
                if (result.code() == Packet.SUCCESS) {
                    JOptionPane.showMessageDialog(reviewDialog, "리뷰가 등록되었습니다.");
                    reviewDialog.dispose();
                } else {
                    JOptionPane.showMessageDialog(reviewDialog, result.body(), "리뷰 등록 실패", JOptionPane.WARNING_MESSAGE);
                }
            });
        });
        panel.add(top, BorderLayout.NORTH);
        panel.add(contentScroll, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        reviewDialog.setContentPane(panel);
        reviewDialog.setVisible(true);
    }

    private JPanel infoCard(String title, String description) {
        JPanel card = new JPanel(new BorderLayout(0, 7));
        card.setBackground(new Color(248, 250, 252));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(232, 236, 240)),
                BorderFactory.createEmptyBorder(9, 13, 9, 13)));
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 14f));
        JTextArea body = new JTextArea(description);
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setColumns(30);
        body.setRows(1);
        body.setFocusable(false);
        body.setOpaque(false);
        body.setFocusable(false);
        body.setForeground(new Color(100, 110, 120));
        body.setFont(body.getFont().deriveFont(12f));
        card.add(heading, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void updateHomePreview() {
        if (type != 0 || form.getComponentCount() == 0) return;
        int viewRow = catalog.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = catalog.convertRowIndexToModel(viewRow);
        String title = String.valueOf(catalogModel.getValueAt(modelRow, 2));
        String category = String.valueOf(catalogModel.getValueAt(modelRow, 2));
        String state = String.valueOf(catalogModel.getValueAt(modelRow, 3));
        for (Component component : ((JPanel) form.getComponent(0)).getComponents()) {
            if (component instanceof JLabel label && "homeSelectedTitle".equals(label.getName())) label.setText(title);
            if (component instanceof JLabel label && "homeSelectedMeta".equals(label.getName())) label.setText(category + "  ·  " + state);
        }
    }

    private void page(int t) {
        type = t;
        projectImage = null;

        entrySwitch.setVisible(t == 11);
        submit.setVisible(t != 0);
        entrySwitch.setText(t == 1 ? "회원가입" : "로그인으로");

        split.setResizeWeight(t == 1 || t == 11 ? 0 : .52);
        split.setDividerLocation(t == 1 ? 106 : t == 11 ? 184 : 290);

        fields.clear();
        form.removeAll();
        output.removeAll();

        heading.setText(t == 0 ? "" : TITLES[t]);

        submit.setText(t == 1 ? "로그인" : t == 2 ? "프로젝트 등록" : t == 15 ? "개인정보 수정" : t == 3 || t == 10 || t == 12 || t == 14 ? "조회" : "실행");
        status.setText(" ");

        if (t == 3) {
            field("조회 방식", new JComboBox<>(new String[]{"전체 목록", "프로젝트 ID", "제목", "카테고리", "상태"}));
            field("검색값 (전체 목록은 생략)", new JTextField());
        } else if (t == 12 || t == 7 || t == 8 || t == 9) {
            JComboBox<MakerProject> choices = new JComboBox<>();
            field("내 프로젝트 선택", choices);
            if (t == 7) {
                for (int i = 1; i < FIELDS[7].length; i++) field(FIELDS[7][i], new JTextField());
                choices.addActionListener(e -> {
                    if (type != 7 || fields.size() < 7 || fields.get(0) != choices) return;
                    MakerProject selected = (MakerProject) choices.getSelectedItem();
                    for (int i = 1; i <= 6; i++) ((JTextField) fields.get(i)).setText(selected == null ? "" : selected.editFields()[i-1]);
                });
            }
            if (t == 9) row("신청 조건", new JLabel("리워드 2개 이상 · 수량 한정 1개 이상"));
            submit.setText(t == 7 ? "수정 저장" : t == 8 ? "리워드 등록" : t == 9 ? "승인 신청" : "조회");
            JButton reload = new JButton("프로젝트 목록 새로고침");
            reload.addActionListener(e -> loadMakerProjects(choices));
            row("목록 관리", reload);
            javax.swing.Timer loader = new javax.swing.Timer(100, null);
            loader.addActionListener(e -> {
                if (type != t || fields.isEmpty() || fields.get(0) != choices) { loader.stop(); return; }
                if (!busy) { loader.stop(); loadMakerProjects(choices); }
            });
            loader.start();
        } else if (t == 15) {
            field("이름", new JTextField());
            field("전화번호 (010-0000-0000)", new JTextField());
            field("자기소개", new JTextField());
            field("현재 비밀번호 (변경 시 입력)", new JPasswordField());
            field("새 비밀번호 (8~72자)", new JPasswordField());
            field("새 비밀번호 확인", new JPasswordField());
        } else {
            for (String s : FIELDS[t]) {
                field(s, s.equals("비밀번호") ? new JPasswordField() : s.equals("처리") ? new JComboBox<>(new String[]{"승인", "거절"}) : new JTextField());
            }
        }

        if (t == 2) {
            JButton choose = new JButton("대표 사진 선택 (JPG/PNG)");

            JLabel imagePreview = new JLabel("선택한 대표 사진이 여기에 표시됩니다.", SwingConstants.CENTER);
            imagePreview.setName("projectImagePreview");
            imagePreview.setPreferredSize(new Dimension(240, 170));
            imagePreview.setMinimumSize(new Dimension(240, 170));
            imagePreview.setOpaque(true);
            imagePreview.setBackground(new Color(248, 249, 251));
            imagePreview.setForeground(new Color(110, 115, 125));
            imagePreview.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(210, 215, 222)),
                    BorderFactory.createEmptyBorder(6, 6, 6, 6)
            ));

            choose.addActionListener(e -> {
                if (busy) return;

                JFileChooser picker = new JFileChooser();
                picker.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JPG / PNG", "jpg", "jpeg", "png"));

                if (picker.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

                try (var input = java.nio.file.Files.newInputStream(picker.getSelectedFile().toPath())) {
                    byte[] bytes = input.readNBytes(10 * 1024 * 1024 + 1);

                    if (bytes.length > 10 * 1024 * 1024) {
                        throw new IOException("사진은 10 MiB 이하로 줄여 주세요.");
                    }

                    ImageIcon icon = new ImageIcon(bytes);

                    if (icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
                        throw new IOException("사진 파일을 읽을 수 없습니다.");
                    }

                    projectImage = java.util.Base64.getEncoder().encodeToString(bytes);
                    choose.setText(picker.getSelectedFile().getName());
                    imagePreview.setText("");
                    imagePreview.setIcon(scaled(icon, 220, 150));
                    status.setText("대표 사진 선택 완료");

                } catch (IOException ex) {
                    projectImage = null;
                    choose.setText("대표 사진 선택 (JPG/PNG)");
                    imagePreview.setIcon(null);
                    imagePreview.setText("사진 미리보기를 표시할 수 없습니다.");
                    status.setText(ex.getMessage());
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "사진 첨부 실패", JOptionPane.ERROR_MESSAGE);
                }
            });

            row("대표 사진", choose);
            row("사진 미리보기", imagePreview);
        }

        if (t == 8) {
            JButton more = new JButton("리워드 행 추가");
            more.addActionListener(e -> {
                if (!busy) reward();
            });

            row("여러 리워드를 한 번에 등록", more);
            reward();
        }

        if (t == 10) {
            row("입력 없이 조회 버튼을 누르세요.", new JLabel());
        }

        centerContent.removeAll();
        if (t == 0) {
            mainSplit.setLeftComponent(null);
            mainSplit.setRightComponent(null);
            centerContent.add(catalogPanel, BorderLayout.CENTER);
        } else {
            mainSplit.setLeftComponent(catalogPanel);
            mainSplit.setRightComponent(work);
            mainSplit.setResizeWeight(.52);
            mainSplit.setDividerSize(0);
            mainSplit.setEnabled(false);
            centerContent.add(mainSplit, BorderLayout.CENTER);
            SwingUtilities.invokeLater(() -> mainSplit.setDividerLocation(.52));
            split.setEnabled(true);
            split.setDividerSize(8);
            split.setLeftComponent(new JScrollPane(form));
            split.setRightComponent(new JScrollPane(output));
        }
        centerContent.revalidate();
        centerContent.repaint();

        form.revalidate();
        form.repaint();
        output.revalidate();
        output.repaint();

        if (t == 15) {
            request(15, "", p -> {
                if (p.code() != Packet.SUCCESS) {
                    status.setText("회원정보 조회 실패: " + p.body());
                    return;
                }
                String[] lines = p.body().split("\n", -1);
                if (lines.length < 2) {
                    status.setText("회원정보를 찾을 수 없습니다.");
                    return;
                }
                String[] data = lines[1].split("\t", -1);
                if (data.length < 6) {
                    status.setText("회원정보 형식이 올바르지 않습니다.");
                    return;
                }
                ((JTextField) fields.get(0)).setText(data[2]);
                ((JTextField) fields.get(1)).setText(data[4]);
                ((JTextField) fields.get(2)).setText(data[5].equals("null") ? "" : data[5]);
                status.setText("회원정보를 불러왔습니다. 비밀번호 변경은 아래 3개 항목을 모두 입력하세요.");
                if (t == 15) {
                    if (role.equals("SUPPORTER")) {
                        request(10, "", history -> {
                            if (history.code() == Packet.SUCCESS) {
                                appendFundingHistory(history.body());
                            } else {
                                status.setText("회원정보는 불러왔지만 후원 내역 조회에 실패했습니다: " + history.body());
                            }
                        });
                    } else {
                        appendFundingHistory("후원 내역은 후원자 계정에서 확인할 수 있습니다.");
                    }
                }
            });
        }
    }

    private String value(JComponent c) {
        if (c instanceof JPasswordField p) {
            char[] chars = p.getPassword();
            try {
                return new String(chars);
            } finally {
                java.util.Arrays.fill(chars, '\0');
            }
        }

        if (c instanceof JTextField f) return f.getText().strip();

        return String.valueOf(((JComboBox<?>) c).getSelectedItem());
    }

    private void send() {
        if (busy) return;

        int t = type;
        var values = new ArrayList<String>();

        for (var c : fields) values.add(value(c));

        if (t == 3) {
            String mode = values.get(0);
            String term = values.get(1);
            values.clear();

            if (!mode.equals("전체 목록")) {
                if (term.isBlank()) {
                    status.setText("검색값을 입력하세요.");
                    return;
                }

                if (!mode.equals("프로젝트 ID")) values.add(mode);
                values.add(term);
            }
        }

        if (t == 12 || t == 7 || t == 8 || t == 9) {
            Object selected = ((JComboBox<?>) fields.get(0)).getSelectedItem();
            if (!(selected instanceof MakerProject project)) { status.setText("프로젝트를 선택하세요."); return; }
            if (t != 12 && (!"대기".equals(project.state()) || !(project.approval().isBlank() || "거절".equals(project.approval())))) {
                JOptionPane.showMessageDialog(this, "미신청 또는 승인 거절된 대기 프로젝트만 처리할 수 있습니다."); return;
            }
            values.set(0, project.id());
            if (t == 9 && JOptionPane.showConfirmDialog(this, project.title() + " 프로젝트의 승인을 신청하시겠습니까?", "승인 신청", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        }

        if (t == 14 && values.get(0).isBlank()) values.clear();

        for (String s : values) {
            if (s.indexOf('\t') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
                status.setText("탭과 줄바꿈은 사용할 수 없어요.");
                return;
            }
        }

        // ▼▼▼ [프로젝트 시작일·종료일 유효성 검증 시작] ▼▼▼
        if (t == 2 || t == 7) {
            int dateOffset = (t == 7) ? 5 : 4;
            try {
                java.time.LocalDate start = java.time.LocalDate.parse(values.get(dateOffset).trim());
                java.time.LocalDate end = java.time.LocalDate.parse(values.get(dateOffset + 1).trim());
                if (start.isBefore(java.time.LocalDate.now())) {
                    String msg = "시작일은 오늘 이후로 입력하세요.";
                    status.setText(msg);
                    JOptionPane.showMessageDialog(this, msg, "입력 오류", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (end.isBefore(start)) {
                    String msg = "종료일은 시작일 이후로 입력하세요.";
                    status.setText(msg);
                    JOptionPane.showMessageDialog(this, msg, "입력 오류", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            } catch (java.time.format.DateTimeParseException ex) {
                String msg = "날짜는 YYYY-MM-DD 형식으로 입력하세요.";
                status.setText(msg);
                JOptionPane.showMessageDialog(this, msg, "형식 오류", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        // ▲▲▲ [프로젝트 시작일·종료일 유효성 검증 끝] ▲▲▲

        if (t == 2) {
            if (projectImage == null) {
                JOptionPane.showMessageDialog(this, "대표 사진을 먼저 선택하세요. 선택에 성공하면 버튼에 파일명이 표시됩니다.", "사진 첨부 필요", JOptionPane.WARNING_MESSAGE);
                return;
            }

            values.add(projectImage);
        }

        if (t == 8) {
            if (values.size() < 6 || (values.size() - 1) % 5 != 0) {
                status.setText("리워드 입력 항목 구성이 올바르지 않습니다. 리워드마다 이름, 설명, 금액, 수량, 사진 항목이 필요합니다.");
                return;
            }

            values.add(0, "images-v1");
        }

        if (t == 15) {
            String currentPassword = values.get(3);
            String newPassword = values.get(4);
            String confirmPassword = values.get(5);
            boolean passwordRequested = !currentPassword.isEmpty() || !newPassword.isEmpty() || !confirmPassword.isEmpty();
            if (passwordRequested) {
                if (currentPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
                    status.setText("비밀번호 변경 시 현재 비밀번호와 새 비밀번호, 확인을 모두 입력하세요.");
                    return;
                }
                if (newPassword.length() < 8 || newPassword.length() > 72) {
                    status.setText("새 비밀번호는 8~72자로 입력하세요.");
                    return;
                }
                if (!newPassword.equals(confirmPassword)) {
                    status.setText("새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
                    return;
                }
            }
        }

        String body = String.join("\t", values);

        if (body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > Packet.MAX) {
            status.setText("사진과 입력값을 합친 크기가 전송 한도를 넘었습니다.");
            return;
        }

        for (var c : fields) {
            if (c instanceof JPasswordField p) p.setText("");
        }

        int requestType = t == 15 ? 19 : t;
        request(requestType, body, p -> {
            if (t == 1) {
                String[] login = p.body().split("\t", 2);
                role = p.code() == Packet.SUCCESS ? login[0] : "";
                name = role.isEmpty() || login.length < 2 ? "" : login[1];

                menu();

                if (!role.isEmpty()) {
                    page(role.equals("ADMIN") ? 14 : role.equals("MAKER") ? 12 : 0);
                }
            }

            if (t == 15 && p.code() == Packet.SUCCESS) {
                name = values.get(0);
                account.setText(switch (role) {
                    case "MAKER" -> "메이커 · " + name;
                    case "SUPPORTER" -> "서포터 · " + name;
                    case "ADMIN" -> "관리자 · " + name;
                    default -> name;
                });
                status.setText(p.body());
                JOptionPane.showMessageDialog(this, p.body(), "수정 완료", JOptionPane.INFORMATION_MESSAGE);
                render(p, requestType);
                return;
            }

            if ((t == 7 || t == 8 || t == 9 || t == 12) && type != t) return;
            if (t == 9 && p.code() == Packet.SUCCESS) {
                render(p, requestType);
                page(9);
                JOptionPane.showMessageDialog(this, p.body());
                return;
            }
            render(p, requestType);

            if (t == 7 && p.code() == Packet.SUCCESS) { page(7); JOptionPane.showMessageDialog(this, p.body()); }
            if (t == 1 || (p.code() == Packet.SUCCESS && (t == 2 || t == 7 || t == 13))) {
                refresh();
            }
        });
    }

    private void request(int t, String body, java.util.function.Consumer<Packet> callback) {
        if (busy) return;

        busy = true;
        submit.setEnabled(false);
        status.setText("서버 응답을 기다리는 중…");

        new SwingWorker<Packet, Void>() {
            protected Packet doInBackground() throws Exception {
                try {
                    if (socket == null) {
                        socket = new Socket();
                        socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 4000);
                        socket.setSoTimeout(120000);
                        in = new DataInputStream(socket.getInputStream());
                        out = new DataOutputStream(socket.getOutputStream());
                    }

                    new Packet(t, Packet.REQUEST, body).write(out);
                    Packet p = Packet.read(in);

                    if (p == null || p.type() != t) {
                        throw new IOException("서버 연결이 종료되었거나 응답 형식이 다릅니다.");
                    }

                    return p;

                } catch (IOException e) {
                    disconnect();
                    throw e;
                }
            }

            protected void done() {
                busy = false;
                submit.setEnabled(true);

                try {
                    callback.accept(get());
                } catch (Exception e) {
                    role = "";
                    name = "";
                    menu();
                    page(0);
                    status.setText("연결 오류: 서버 실행 상태와 포트를 확인한 뒤 다시 시도하세요.");
                }
            }
        }.execute();
    }

    private void refresh() {
        request(16, pending.isSelected() && role.equals("ADMIN") ? "pending" : "", p -> {
            catalogModel.setRowCount(0);

            if (p.code() != Packet.SUCCESS) {
                syncCategoryFilterOptions();
                applyCatalogFilterAndSort();
                status.setText(p.body());
                return;
            }

            String[] lines = p.body().split("\n");

            for (int i = 1; i < lines.length; i++) {
                String[] cells = lines[i].split("\t", -1);
                if (cells.length >= 4) {
                    catalogModel.addRow(new Object[]{cells[0], null, cells[1], cells[2], cells[3]});
                }
            }

            syncCategoryFilterOptions();
            applyCatalogFilterAndSort();
            status.setText("프로젝트 " + catalog.getRowCount() + "개 표시 (전체 " + catalogModel.getRowCount() + "개)");
            if (type == 0 && catalog.getRowCount() > 0 && catalog.getSelectedRow() < 0) catalog.setRowSelectionInterval(0, 0);
            updateHomePreview();
            loadCatalogImages(0);
        });
    }

    private void loadCatalogImages(int modelRow) {
        if (modelRow >= catalogModel.getRowCount()) return;
        String projectId = String.valueOf(catalogModel.getValueAt(modelRow, 0));
        request(18, projectId, result -> {
            if (result.code() == Packet.SUCCESS) {
                try {
                    ImageIcon image = new ImageIcon(java.util.Base64.getDecoder().decode(result.body()));
                    if (image.getIconWidth() > 0 && image.getIconHeight() > 0) {
                        catalogModel.setValueAt(scaled(image, 126, 96), modelRow, 1);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
            loadCatalogImages(modelRow + 1);
        });
    }

    private String selected() {
        int viewRow = catalog.getSelectedRow();

        if (viewRow < 0) {
            status.setText("목록에서 프로젝트를 선택하세요.");
            return null;
        }

        int modelRow = catalog.convertRowIndexToModel(viewRow);
        return String.valueOf(catalogModel.getValueAt(modelRow, 0));
    }

    private void openProject() {
        if (busy) return;

        int viewRow = catalog.getSelectedRow();
        String id = selected();
        if (id == null || viewRow < 0) return;

        int modelRow = catalog.convertRowIndexToModel(viewRow);
        String titleText = String.valueOf(catalogModel.getValueAt(modelRow, 2));
        openProject(id, titleText, null);
    }

    private void openProject(String id, String titleText, Component failureOwner) {
        if (busy) return;

        request(3, id, p -> {
            if (p.code() != Packet.SUCCESS) {
                if (failureOwner == null) render(p, 3);
                else JOptionPane.showMessageDialog(failureOwner, p.body(), "프로젝트 상세", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String[] blocks = p.body().split("\n\n", 2);
            String[] project = blocks[0].split("\n", 2);
            String[] data = project.length == 2 ? project[1].split("\t", -1) : new String[0];

            Color ink = new Color(35, 39, 42);
            Color muted = new Color(112, 119, 125);
            Color line = new Color(230, 233, 236);
            Color soft = new Color(247, 248, 249);
            Color accent = new Color(39, 112, 91);

            JDialog dialog = new JDialog(this, "프로젝트 상세", false);
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.setSize(1120, 740);
            dialog.setMinimumSize(new Dimension(900, 620));
            dialog.setLocationRelativeTo(this);

            // ▼▼▼ [리워드 추가 후원 기능 수정 시작 - 기존 후원 이력 및 제자리 갱신용 참조 준비] ▼▼▼
            java.util.Set<String> backedRewardIds = new java.util.HashSet<>();
            java.util.Set<String> backedProjectIds = new java.util.HashSet<>();
            List<Runnable> refreshFundButtons = new ArrayList<>();
            final Runnable[] reloadDetailRef = new Runnable[1];
            // ▲▲▲ [리워드 추가 후원 기능 수정 끝 - 기존 후원 이력 및 제자리 갱신용 참조 준비] ▲▲▲

            JPanel root = new JPanel(new BorderLayout(28, 0));
            root.setBackground(Color.WHITE);
            root.setBorder(BorderFactory.createEmptyBorder(26, 28, 26, 28));

            JPanel left = new JPanel(new BorderLayout(0, 12));
            left.setBackground(Color.WHITE);
            left.setPreferredSize(new Dimension(450, 0));

            JLabel hero = new JLabel("대표 이미지를 불러오는 중", SwingConstants.CENTER);
            hero.setOpaque(true);
            hero.setBackground(soft);
            hero.setForeground(muted);
            hero.setBorder(BorderFactory.createLineBorder(line));
            hero.setPreferredSize(new Dimension(450, 450));
            left.add(hero, BorderLayout.CENTER);

            JLabel imageLabel = new JLabel("PROJECT IMAGE");
            imageLabel.setForeground(muted);
            imageLabel.setFont(imageLabel.getFont().deriveFont(Font.BOLD, 11f));
            left.add(imageLabel, BorderLayout.SOUTH);

            JPanel right = new JPanel(new BorderLayout(0, 18));
            right.setBackground(Color.WHITE);

            JPanel summary = new JPanel();
            summary.setBackground(Color.WHITE);
            summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));

            JLabel eyebrow = new JLabel("PROJECT DETAIL");
            eyebrow.setForeground(accent);
            eyebrow.setFont(eyebrow.getFont().deriveFont(Font.BOLD, 12f));
            summary.add(eyebrow);
            summary.add(Box.createVerticalStrut(8));

            JLabel title = new JLabel("<html><div style='width:570px'>" + escapeHtml(titleText) + "</div></html>");
            title.setForeground(ink);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 32f));
            summary.add(title);
            summary.add(Box.createVerticalStrut(12));

            boolean supporter = "SUPPORTER".equals(role);
            boolean likeStateAvailable = data.length > 10 && ("1".equals(data[10]) || "0".equals(data[10]));
            JButton likeButton = new JButton("♡ 좋아요");
            final boolean[] liked = {likeStateAvailable && "1".equals(data[10])};
            likeButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            likeButton.setForeground(Color.BLACK);
            likeButton.setText(liked[0] ? "♥ 좋아요 취소" : "♡ 좋아요");
            likeButton.setEnabled(supporter && likeStateAvailable);

            // ▼▼▼ [평균 별점·리뷰 목록·좋아요 수 시작 - 좋아요 수/평균 별점 표시] ▼▼▼
            final int[] likeCount = {0};
            if (data.length > 11) {
                try { likeCount[0] = Integer.parseInt(data[11].trim()); } catch (NumberFormatException ignored) {}
            }
            JLabel likeCountLabel = new JLabel("♥ " + likeCount[0] + "명");
            likeCountLabel.setForeground(new Color(214, 51, 108));
            likeCountLabel.setFont(likeCountLabel.getFont().deriveFont(Font.BOLD, 15f));
            likeCountLabel.setToolTipText("이 프로젝트가 받은 좋아요 수");

            JLabel ratingLabel = new JLabel("★ 리뷰 불러오는 중");
            ratingLabel.setForeground(new Color(232, 160, 0));
            ratingLabel.setFont(ratingLabel.getFont().deriveFont(Font.BOLD, 15f));
            // ▲▲▲ [평균 별점·리뷰 목록·좋아요 수 끝 - 좋아요 수/평균 별점 표시] ▲▲▲
            Socket detailSession = socket;
            likeButton.addActionListener(likeEvent -> {
                likeButton.setEnabled(false);
                Timer timer = new Timer(100, null);
                timer.addActionListener(event -> {
                    if (!dialog.isDisplayable() || socket != detailSession || !"SUPPORTER".equals(role)) {
                        timer.stop();
                        return;
                    }
                    if (busy) return;
                    timer.stop();
                    boolean cancel = liked[0];
                    request(cancel ? 21 : 5, id, result -> {
                        if (!dialog.isDisplayable()) return;
                        likeButton.setEnabled(true);
                        if (result.code() == Packet.SUCCESS) {
                            liked[0] = !cancel;
                            likeButton.setText(liked[0] ? "♥ 좋아요 취소" : "♡ 좋아요");
                            // 받은 좋아요 수를 화면에서 바로 +1 / -1
                            likeCount[0] = Math.max(0, likeCount[0] + (cancel ? -1 : 1));
                            likeCountLabel.setText("♥ " + likeCount[0] + "명");
                        } else {
                            JOptionPane.showMessageDialog(dialog, result.body(), "좋아요 처리", JOptionPane.INFORMATION_MESSAGE);
                        }
                    });
                });
                timer.start();
            });
            // 좋아요 버튼 · 받은 좋아요 수 · 평균 별점을 한 줄로 배치
            JPanel likeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            likeRow.setBackground(Color.WHITE);
            likeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            likeRow.add(likeButton);
            likeRow.add(Box.createHorizontalStrut(14));
            likeRow.add(likeCountLabel);
            likeRow.add(Box.createHorizontalStrut(18));
            likeRow.add(ratingLabel);
            likeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, likeRow.getPreferredSize().height));
            summary.add(likeRow);
            summary.add(Box.createVerticalStrut(16));

            // ▼▼▼ [리워드 추가 후원 기능 수정 시작 - 모금/달성률/성공배지 제자리 갱신용 참조] ▼▼▼
            JLabel[] fundingAmountRef = new JLabel[1];
            JProgressBar[] progressRef = new JProgressBar[1];
            JLabel[] percentLabelRef = new JLabel[1];
            JLabel[] successBadgeRef = new JLabel[1];
            // ▲▲▲ [리워드 추가 후원 기능 수정 끝 - 모금/달성률/성공배지 제자리 갱신용 참조] ▲▲▲

            if (data.length >= 8) {
                JPanel tags = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                tags.setBackground(Color.WHITE);
                tags.setAlignmentX(Component.LEFT_ALIGNMENT);
                tags.add(infoChip(data[2], soft, ink));
                tags.add(infoChip(data[7], new Color(235, 245, 240), accent));
                summary.add(tags);
                summary.add(Box.createVerticalStrut(15));

                JPanel metrics = new JPanel(new GridLayout(1, 2, 10, 0));
                metrics.setBackground(Color.WHITE);
                metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
                metrics.add(metricBox("목표 금액", formatMoney(data[4]) + "원", soft, ink, muted));
                metrics.add(metricBox("펀딩 기간", data[5] + " ~ " + data[6], soft, ink, muted));
                summary.add(metrics);
                summary.add(Box.createVerticalStrut(13));

                JPanel fundingBox = new JPanel();
                fundingBox.setBackground(Color.WHITE);
                fundingBox.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(line),
                        BorderFactory.createEmptyBorder(13, 15, 13, 15)
                ));
                fundingBox.setLayout(new BoxLayout(fundingBox, BoxLayout.Y_AXIS));

                JLabel fundingCaption = new JLabel("실시간 모금 현황");
                fundingCaption.setForeground(muted);
                fundingCaption.setFont(fundingCaption.getFont().deriveFont(Font.BOLD, 12f));
                fundingBox.add(fundingCaption);
                fundingBox.add(Box.createVerticalStrut(7));

                String pledged = data.length > 8 ? data[8] : "0";
                String achievement = data.length > 9 ? data[9] : "0";
                JLabel fundingAmount = new JLabel(formatMoney(pledged) + "원 모금");
                fundingAmount.setForeground(ink);
                fundingAmount.setFont(fundingAmount.getFont().deriveFont(Font.BOLD, 24f));
                fundingBox.add(fundingAmount);
                fundingBox.add(Box.createVerticalStrut(8));

                double pct = 0;
                try {
                    pct = Double.parseDouble(achievement);
                } catch (Exception ignored) {}
                pct = Math.max(0.0, pct);

                int scaled = (int) Math.round(pct * 10);
                JProgressBar progress = new JProgressBar(0, Math.max(1000, scaled));
                progress.setValue(scaled);
                progress.setStringPainted(false);
                progress.setForeground(accent);
                progress.setBackground(new Color(235, 238, 240));
                progress.setBorderPainted(false);
                progress.setPreferredSize(new Dimension(600, 13));
                progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 13));
                fundingBox.add(progress);
                fundingBox.add(Box.createVerticalStrut(6));

                JPanel percentRow = new JPanel();
                percentRow.setOpaque(false);
                percentRow.setLayout(new BoxLayout(percentRow, BoxLayout.X_AXIS));
                percentRow.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel percentLabel = new JLabel(String.format(Locale.KOREA, "목표 달성률 %.2f%%", pct));
                percentLabel.setForeground(accent);
                percentLabel.setFont(percentLabel.getFont().deriveFont(Font.BOLD, 15f));
                percentRow.add(percentLabel);

                JLabel successBadge = new JLabel("펀딩 성공!");
                successBadge.setForeground(new Color(22, 163, 74));
                successBadge.setFont(successBadge.getFont().deriveFont(Font.BOLD, 15f));
                successBadge.setVisible(pct >= 100.0);
                percentRow.add(Box.createHorizontalStrut(8));
                percentRow.add(successBadge);

                // ▼▼▼ [리워드 추가 후원 기능 수정 시작 - 참조 연결] ▼▼▼
                fundingAmountRef[0] = fundingAmount;
                progressRef[0] = progress;
                percentLabelRef[0] = percentLabel;
                successBadgeRef[0] = successBadge;
                // ▲▲▲ [리워드 추가 후원 기능 수정 끝 - 참조 연결] ▲▲▲

                fundingBox.add(percentRow);
                fundingBox.add(Box.createVerticalStrut(6));

                summary.add(fundingBox);
                summary.add(Box.createVerticalStrut(13));

                JTextArea description = new JTextArea(data[3].replace("\\n", "\n"));
                description.setEditable(false);
                description.setLineWrap(true);
                description.setWrapStyleWord(true);
                description.setFont(description.getFont().deriveFont(15f));
                description.setForeground(new Color(85, 91, 96));
                description.setBackground(Color.WHITE);
                description.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
                description.setRows(3);
                description.setAlignmentX(Component.LEFT_ALIGNMENT);
                summary.add(description);
            }

            JPanel rewards = new JPanel();
            rewards.setBackground(Color.WHITE);
            rewards.setLayout(new BoxLayout(rewards, BoxLayout.Y_AXIS));

            JPanel rewardHeader = new JPanel(new BorderLayout());
            rewardHeader.setBackground(Color.WHITE);
            JLabel rewardTitle = new JLabel("리워드 구성");
            rewardTitle.setForeground(ink);
            rewardTitle.setFont(rewardTitle.getFont().deriveFont(Font.BOLD, 21f));
            rewardHeader.add(rewardTitle, BorderLayout.WEST);

            ArrayList<String> rewardIds = new ArrayList<>();
            ArrayList<JLabel> rewardPhotos = new ArrayList<>();

            if (blocks.length == 2) {
                String[] lines = blocks[1].split("\n");
                for (int i = 1; i < lines.length; i++) {
                    String[] r = lines[i].split("\t", -1);
                    if (r.length >= 5) rewardIds.add(r[0]);
                }
            }

            JLabel rewardCount = new JLabel(rewardIds.size() + "개");
            rewardCount.setForeground(muted);
            rewardCount.setFont(rewardCount.getFont().deriveFont(12f));
            rewardHeader.add(rewardCount, BorderLayout.EAST);
            rewards.add(rewardHeader);
            rewards.add(Box.createVerticalStrut(12));

            // ▼▼▼ [리워드 추가 후원 기능 수정 시작 - 리워드 카드 생성 및 추가 후원 처리] ▼▼▼
            java.util.function.Consumer<String> rebuildRewardCards = rewardBlockText -> {
                rewards.removeAll();
                rewardIds.clear();
                rewardPhotos.clear();

                if (rewardBlockText != null && !rewardBlockText.isBlank()) {
                    String[] lines = rewardBlockText.split("\n");
                    for (int i = 1; i < lines.length; i++) {
                        String[] r = lines[i].split("\t", -1);
                        if (r.length >= 5) rewardIds.add(r[0]);
                    }
                }

                rewardCount.setText(rewardIds.size() + "개");
                rewards.add(rewardHeader);
                rewards.add(Box.createVerticalStrut(12));

                if (rewardBlockText != null && !rewardBlockText.isBlank()) {
                    String[] lines = rewardBlockText.split("\n");
                    for (int i = 1; i < lines.length; i++) {
                        String[] r = lines[i].split("\t", -1);
                        if (r.length < 5) continue;

                        JPanel card = new JPanel(new BorderLayout(14, 0));
                        card.setBackground(Color.WHITE);
                        card.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(line),
                                BorderFactory.createEmptyBorder(12, 14, 12, 14)
                        ));
                        card.setPreferredSize(new Dimension(680, 166));
                        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 166));
                        card.setAlignmentX(Component.LEFT_ALIGNMENT);

                        JPanel details = new JPanel();
                        details.setBackground(Color.WHITE);
                        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));

                        JLabel rewardName = new JLabel(escapeHtml(r[1]));
                        rewardName.setForeground(ink);
                        rewardName.setFont(rewardName.getFont().deriveFont(Font.BOLD, 17f));
                        details.add(rewardName);
                        details.add(Box.createVerticalStrut(7));

                        JLabel price = new JLabel(formatMoney(r[3]) + "원  ·  " + (r[4].isBlank() ? "수량 제한 없음" : "잔여 " + r[4] + "개"));
                        price.setForeground(accent);
                        price.setFont(price.getFont().deriveFont(Font.BOLD, 15f));
                        details.add(price);

                        if (!r[2].isBlank()) {
                            details.add(Box.createVerticalStrut(5));
                            JLabel desc = new JLabel("<html><div style='width:390px;color:#777777'>" + escapeHtml(r[2]) + "</div></html>");
                            desc.setFont(desc.getFont().deriveFont(14f));
                            details.add(desc);
                        }

                        JPanel rewardActions = new JPanel();
                        rewardActions.setOpaque(false);
                        rewardActions.setLayout(new BoxLayout(rewardActions, BoxLayout.Y_AXIS));

                        String rewardId = r[0].trim();
                        boolean alreadyBacked = backedRewardIds.contains(rewardId) || backedProjectIds.contains(id.trim());

                        JButton fundButton = new JButton(alreadyBacked ? "추가 후원하기" : "이 리워드 후원하기");
                        fundButton.setEnabled("SUPPORTER".equals(role));
                        fundButton.setToolTipText(
                                role.isEmpty() ? "후원하려면 먼저 로그인하세요."
                                        : !"SUPPORTER".equals(role) ? "후원자 계정으로만 후원할 수 있습니다."
                                        : alreadyBacked ? "이미 후원한 리워드에 추가로 후원합니다." : "선택한 리워드를 후원합니다."
                        );

                        refreshFundButtons.add(() -> {
                            boolean nowBacked = backedRewardIds.contains(rewardId) || backedProjectIds.contains(id.trim());
                            fundButton.setText(nowBacked ? "추가 후원하기" : "이 리워드 후원하기");
                            fundButton.setEnabled("SUPPORTER".equals(role));
                        });

                        fundButton.addActionListener(ev -> {
                            if (!"SUPPORTER".equals(role)) {
                                JOptionPane.showMessageDialog(dialog,
                                        role.isEmpty() ? "리워드 후원은 로그인 후 이용할 수 있습니다." : "후원자 계정으로 로그인해야 합니다.",
                                        "로그인 필요", JOptionPane.INFORMATION_MESSAGE);
                                return;
                            }

                            boolean isAdditional = backedRewardIds.contains(rewardId) || backedProjectIds.contains(id.trim());
                            String prompt = isAdditional
                                    ? "이미 후원하신 리워드입니다. 추가로 후원할 수량을 입력하세요."
                                    : "후원 수량을 입력하세요.";

                            String quantityText = JOptionPane.showInputDialog(dialog, prompt, "1");
                            if (quantityText == null) return;

                            int quantity;
                            try {
                                quantity = Integer.parseInt(quantityText.trim());
                            } catch (NumberFormatException ex) {
                                JOptionPane.showMessageDialog(dialog, "수량은 숫자로 입력하세요.");
                                return;
                            }

                            if (quantity <= 0) {
                                JOptionPane.showMessageDialog(dialog, "수량은 1개 이상이어야 합니다.");
                                return;
                            }

                            fundButton.setEnabled(false);
                            request(4, id + "\t" + rewardId + "\t" + quantity, result -> {
                                fundButton.setEnabled(true);
                                if (result.code() == Packet.SUCCESS) {
                                    backedRewardIds.add(rewardId);
                                    backedProjectIds.add(id.trim());
                                    fundButton.setText("추가 후원하기");

                                    JOptionPane.showMessageDialog(dialog,
                                            (isAdditional ? "추가 후원이 완료되었습니다." : "후원이 완료되었습니다.")
                                                    + "\n결제 금액: " + formatMoney(result.body()) + "원",
                                            "후원 완료", JOptionPane.INFORMATION_MESSAGE);

                                    refresh();
                                    if (dialog.isDisplayable() && reloadDetailRef[0] != null) {
                                        reloadDetailRef[0].run();
                                    }
                                } else {
                                    JOptionPane.showMessageDialog(dialog, result.body(),
                                            "후원 실패", JOptionPane.WARNING_MESSAGE);
                                }
                            });
                        });

                        rewardActions.add(fundButton);
                        card.add(details, BorderLayout.CENTER);
                        card.add(rewardActions, BorderLayout.SOUTH);

                        JLabel photo = new JLabel("사진 없음", SwingConstants.CENTER);
                        photo.setOpaque(true);
                        photo.setBackground(soft);
                        photo.setForeground(muted);
                        photo.setBorder(BorderFactory.createLineBorder(line));
                        photo.setPreferredSize(new Dimension(140, 140));
                        card.add(photo, BorderLayout.EAST);
                        rewardPhotos.add(photo);

                        rewards.add(card);
                        rewards.add(Box.createVerticalStrut(9));
                    }
                }

                rewards.revalidate();
                rewards.repaint();
            };

            rebuildRewardCards.accept(blocks.length == 2 ? blocks[1] : "");
            // ▲▲▲ [리워드 추가 후원 기능 수정 끝 - 리워드 카드 생성 및 추가 후원 처리] ▲▲▲

            // ▼▼▼ [평균 별점·리뷰 목록·좋아요 수 시작 - 리뷰 목록 영역] ▼▼▼
            JPanel reviews = new JPanel();
            reviews.setBackground(Color.WHITE);
            reviews.setLayout(new BoxLayout(reviews, BoxLayout.Y_AXIS));
            reviews.setAlignmentX(Component.LEFT_ALIGNMENT);

            java.util.function.Consumer<Packet> showReviews = res -> {
                reviews.removeAll();

                JPanel reviewHeader = new JPanel(new BorderLayout());
                reviewHeader.setBackground(Color.WHITE);
                reviewHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
                reviewHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
                JLabel reviewTitle = new JLabel("리뷰");
                reviewTitle.setForeground(ink);
                reviewTitle.setFont(reviewTitle.getFont().deriveFont(Font.BOLD, 21f));
                reviewHeader.add(reviewTitle, BorderLayout.WEST);
                reviews.add(reviewHeader);
                reviews.add(Box.createVerticalStrut(12));

                if (res == null || res.code() != Packet.SUCCESS) {
                    ratingLabel.setText("★ 리뷰 정보 없음");
                    reviews.add(reviewMessage(res == null ? "리뷰를 불러오지 못했습니다." : res.body(), muted));
                } else {
                    // 블록1: avg_rating, review_count / 블록2: 작성자, 별점, 내용, 작성일
                    String[] rBlocks = res.body().split("\n\n", 2);
                    String[] head = rBlocks[0].split("\n", 2);
                    String[] stat = head.length == 2 ? head[1].split("\t", -1) : new String[0];
                    String avg = stat.length > 0 ? stat[0].trim() : "";
                    int count = 0;
                    try { count = stat.length > 1 ? Integer.parseInt(stat[1].trim()) : 0; } catch (NumberFormatException ignored) {}

                    if (count == 0 || avg.isEmpty()) {
                        ratingLabel.setText("☆ 리뷰 없음");
                    } else {
                        double avgValue = 0;
                        try { avgValue = Double.parseDouble(avg); } catch (NumberFormatException ignored) {}
                        ratingLabel.setText(stars(avgValue) + "  " + String.format(Locale.KOREA, "%.1f", avgValue) + "  (리뷰 " + count + "개)");
                    }
                    reviewTitle.setText("리뷰 " + count + "개");

                    int shown = 0;
                    if (rBlocks.length == 2) {
                        String[] lines = rBlocks[1].split("\n");
                        for (int i = 1; i < lines.length; i++) {
                            String[] r = lines[i].split("\t", -1);
                            if (r.length < 3) continue;
                            int rating = 0;
                            try { rating = Integer.parseInt(r[1].trim()); } catch (NumberFormatException ignored) {}
                            reviews.add(reviewCard(unescapeCell(r[0]), rating, unescapeCell(r[2]), r.length > 3 ? r[3] : "", ink, muted, line));
                            reviews.add(Box.createVerticalStrut(8));
                            shown++;
                        }
                    }
                    if (shown == 0) reviews.add(reviewMessage("아직 작성된 리뷰가 없습니다.", muted));
                }

                reviews.revalidate();
                reviews.repaint();
            };
            showReviews.accept(new Packet(23, Packet.SUCCESS, "avg_rating\treview_count\n\t0"));
            ratingLabel.setText("★ 리뷰 불러오는 중");

            // 리워드 카드 아래에 리뷰 목록을 같은 스크롤 안에 둔다.
            rewards.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel rewardAndReviews = new JPanel();
            rewardAndReviews.setBackground(Color.WHITE);
            rewardAndReviews.setLayout(new BoxLayout(rewardAndReviews, BoxLayout.Y_AXIS));
            rewardAndReviews.add(rewards);
            rewardAndReviews.add(Box.createVerticalStrut(22));
            rewardAndReviews.add(reviews);
            // ▲▲▲ [평균 별점·리뷰 목록·좋아요 수 끝 - 리뷰 목록 영역] ▲▲▲

            JScrollPane rewardScroll = new JScrollPane(rewardAndReviews);
            rewardScroll.setBorder(BorderFactory.createEmptyBorder());
            rewardScroll.getViewport().setBackground(Color.WHITE);
            rewardScroll.getVerticalScrollBar().setUnitIncrement(16);

            JPanel rightContent = new JPanel(new BorderLayout(0, 18));
            rightContent.setBackground(Color.WHITE);
            rightContent.add(summary, BorderLayout.NORTH);
            rightContent.add(rewardScroll, BorderLayout.CENTER);

            root.add(left, BorderLayout.WEST);
            root.add(rightContent, BorderLayout.CENTER);

            dialog.setContentPane(root);
            dialog.setVisible(true);

            // ▼▼▼ [리워드 추가 후원 기능 수정 시작 - 이미지/후원내역 조회 및 제자리 갱신] ▼▼▼
            Runnable loadImagesAndHistory = () -> {
                if ("SUPPORTER".equals(role)) {
                    Timer historyTimer = new Timer(100, null);
                    historyTimer.addActionListener(event -> {
                        if (!dialog.isDisplayable() || socket != detailSession || !"SUPPORTER".equals(role)) {
                            historyTimer.stop();
                            return;
                        }
                        if (busy) return;
                        historyTimer.stop();

                        request(10, "", historyRes -> {
                            if (!dialog.isDisplayable()) return;
                            if (historyRes.code() == Packet.SUCCESS && historyRes.body() != null && !historyRes.body().isBlank()) {
                                String[] hLines = historyRes.body().split("\n", -1);
                                for (int idx = 1; idx < hLines.length; idx++) {
                                    if (hLines[idx].isBlank()) continue;
                                    String[] cols = hLines[idx].split("\t", -1);
                                    if (cols.length >= 1) backedProjectIds.add(cols[0].trim());
                                    if (cols.length >= 2) backedRewardIds.add(cols[1].trim());
                                    if (cols.length >= 7) {
                                        backedProjectIds.add(cols[6].trim());
                                        backedProjectIds.add(cols[cols.length - 1].trim());
                                    }
                                }
                                for (Runnable r : refreshFundButtons) {
                                    r.run();
                                }
                            }
                        });
                    });
                    historyTimer.setInitialDelay(0);
                    historyTimer.start();
                }

                // 평균 별점·리뷰 목록(TYPE 23)을 먼저 받고, 이어서 대표 이미지(TYPE 18)를 받는다.
                request(23, id, reviewRes -> {
                    if (!dialog.isDisplayable()) return;
                    showReviews.accept(reviewRes);

                    request(18, id, res -> {
                        if (!dialog.isDisplayable()) return;

                        if (res.code() != Packet.SUCCESS) {
                            hero.setText("대표 이미지를 불러오지 못했습니다.");
                        } else {
                            try {
                                ImageIcon icon = new ImageIcon(java.util.Base64.getDecoder().decode(res.body()));
                                if (icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
                                    hero.setText("");
                                    hero.setIcon(scaled(icon, 440, 440));
                                } else {
                                    hero.setText("대표 이미지가 없습니다.");
                                }
                            } catch (IllegalArgumentException ex) {
                                hero.setText("대표 이미지 데이터 오류");
                            }
                        }

                        loadRewardImages(dialog, id, rewardIds, rewardPhotos, 0);
                    });
                });
            };

            reloadDetailRef[0] = () -> {
                if (!dialog.isDisplayable()) return;
                Timer detailTimer = new Timer(100, null);
                detailTimer.addActionListener(event -> {
                    if (!dialog.isDisplayable() || socket != detailSession) {
                        detailTimer.stop();
                        return;
                    }
                    if (busy) return;
                    detailTimer.stop();

                    request(3, id, updated -> {
                        if (!dialog.isDisplayable() || updated.code() != Packet.SUCCESS) return;
                        String[] uBlocks = updated.body().split("\n\n", 2);
                        String[] uProject = uBlocks[0].split("\n", 2);
                        String[] uData = uProject.length == 2 ? uProject[1].split("\t", -1) : new String[0];

                        if (uData.length >= 8 && fundingAmountRef[0] != null) {
                            String uPledged = uData.length > 8 ? uData[8] : "0";
                            String uAch = uData.length > 9 ? uData[9] : "0";
                            double uPct = 0;
                            try {
                                uPct = Double.parseDouble(uAch);
                            } catch (Exception ignored) {}
                            uPct = Math.max(0.0, uPct);
                            int uScaled = (int) Math.round(uPct * 10);

                            fundingAmountRef[0].setText(formatMoney(uPledged) + "원 모금");
                            progressRef[0].setMaximum(Math.max(1000, uScaled));
                            progressRef[0].setValue(uScaled);
                            percentLabelRef[0].setText(String.format(Locale.KOREA, "목표 달성률 %.2f%%", uPct));
                            if (successBadgeRef[0] != null) {
                                successBadgeRef[0].setVisible(uPct >= 100.0);
                            }
                        }

                        if (uData.length > 11) {
                            try {
                                likeCount[0] = Integer.parseInt(uData[11].trim());
                                likeCountLabel.setText("♥ " + likeCount[0] + "명");
                            } catch (NumberFormatException ignored) {}
                        }

                        refreshFundButtons.clear();
                        rebuildRewardCards.accept(uBlocks.length == 2 ? uBlocks[1] : "");
                        loadImagesAndHistory.run();
                    });
                });
                detailTimer.setInitialDelay(0);
                detailTimer.start();
            };

            loadImagesAndHistory.run();
            // ▲▲▲ [리워드 추가 후원 기능 수정 끝 - 이미지/후원내역 조회 및 제자리 갱신] ▲▲▲
        });
    }

    // ▼▼▼ [평균 별점·리뷰 목록·좋아요 수 시작 - 리뷰 표시 도우미] ▼▼▼
    private String stars(double rating) {
        int full = (int) Math.round(Math.max(0, Math.min(5, rating)));
        return "★".repeat(full) + "☆".repeat(5 - full);
    }

    private JPanel reviewCard(String writer, int rating, String content, String date, Color ink, Color muted, Color line) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(line),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel head = new JLabel("<html><span style='color:#E8A000'>" + stars(rating) + "</span>&nbsp;&nbsp;<b>"
                + escapeHtml(writer) + "</b>"
                + (date.isBlank() ? "" : "&nbsp;<span style='color:#70777D'>· " + escapeHtml(date) + "</span>") + "</html>");
        head.setForeground(ink);
        head.setFont(head.getFont().deriveFont(14f));
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(head);
        card.add(Box.createVerticalStrut(5));

        JLabel body = new JLabel("<html><div style='width:560px'>" + escapeHtml(content) + "</div></html>");
        body.setForeground(new Color(85, 91, 96));
        body.setFont(body.getFont().deriveFont(14f));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(body);

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private JLabel reviewMessage(String text, Color muted) {
        JLabel label = new JLabel(text);
        label.setForeground(muted);
        label.setFont(label.getFont().deriveFont(14f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        return label;
    }
    // ▲▲▲ [평균 별점·리뷰 목록·좋아요 수 끝 - 리뷰 표시 도우미] ▲▲▲

    private JPanel infoChip(String value, Color background, Color foreground) {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 5));
        chip.setBackground(background);
        JLabel label = new JLabel(escapeHtml(value));
        label.setForeground(foreground);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        chip.add(label);
        return chip;
    }

    private JPanel metricBox(String label, String value, Color background, Color foreground, Color muted) {
        JPanel box = new JPanel();
        box.setBackground(background);
        box.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        JLabel caption = new JLabel(label);
        caption.setForeground(muted);
        caption.setFont(caption.getFont().deriveFont(11f));

        JLabel content = new JLabel("<html><div style='width:220px'>" + escapeHtml(value) + "</div></html>");
        content.setForeground(foreground);
        content.setFont(content.getFont().deriveFont(Font.BOLD, 13f));

        box.add(caption);
        box.add(Box.createVerticalStrut(5));
        box.add(content);
        return box;
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("\n", "<br>");
    }

    private String formatMoney(String value) {
        try {
            java.text.DecimalFormat formatter = new java.text.DecimalFormat("#,##0.##");
            return formatter.format(new java.math.BigDecimal(value));
        } catch (Exception e) {
            return value;
        }
    }

    private void loadRewardImages(JDialog dialog, String project, ArrayList<String> ids, ArrayList<JLabel> photos, int index) {
        if (!dialog.isDisplayable() || index >= ids.size()) return;

        request(17, project + "\t" + ids.get(index), p -> {
            if (!dialog.isDisplayable()) return;

            if (p.code() == Packet.SUCCESS) {
                try {
                    ImageIcon icon = new ImageIcon(java.util.Base64.getDecoder().decode(p.body()));
                    if (icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
                        JLabel photo = photos.get(index);
                        photo.setText("");
                        photo.setIcon(scaled(icon, 134, 134));
                        photo.setToolTipText("리워드 사진");
                        photo.revalidate();
                        photo.repaint();
                    }
                } catch (IllegalArgumentException ignored) {}
            }

            loadRewardImages(dialog, project, ids, photos, index + 1);
        });
    }

    private ImageIcon scaled(ImageIcon source, int width, int height) {
        double ratio = Math.min((double) width / source.getIconWidth(), (double) height / source.getIconHeight());
        return new ImageIcon(source.getImage().getScaledInstance(
                Math.max(1, (int) (source.getIconWidth() * ratio)),
                Math.max(1, (int) (source.getIconHeight() * ratio)),
                Image.SCALE_SMOOTH
        ));
    }

    private void appendFundingHistory(String body) {
        output.add(new JSeparator());
        JLabel title = new JLabel("내 후원 내역");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        output.add(title);

        if (body == null || body.isBlank()) {
            output.add(new JLabel("후원 내역이 없습니다."));
            output.revalidate();
            output.repaint();
            return;
        }

        if (body.startsWith("후원 내역은 후원자 계정")) {
            output.add(new JLabel(body));
            output.revalidate();
            output.repaint();
            return;
        }

        String[] lines = body.split("\\n", -1);
        if (lines.length == 0 || lines[0].isBlank()) {
            output.add(new JLabel("후원 내역이 없습니다."));
            output.revalidate();
            output.repaint();
            return;
        }

        DefaultTableModel model = new DefaultTableModel(lines[0].split("\\t", -1), 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) model.addRow(lines[i].split("\\t", -1));
        }
        JTable table = new JTable(model);
        table.setRowHeight(25);
        table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int j = 0; j < table.getColumnCount(); j++) {
            table.getColumnModel().getColumn(j).setPreferredWidth(
                    Math.max(115, table.getColumnName(j).length() * 10));
        }
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(570, Math.min(240, 58 + 25 * model.getRowCount())));
        output.add(scroll);
        if (model.getRowCount() == 0) output.add(new JLabel("후원 내역이 없습니다."));
        output.revalidate();
        output.repaint();
    }

    private void disconnect() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }

        socket = null;
    }

    private record MakerProject(String id, String title, String category, String state, String[] editFields, String approval) {
        @Override public String toString() { return title + " · " + category + " · " + state + (approval.isBlank() ? "" : " · 심사: " + approval); }
    }

    private void loadMakerProjects(JComboBox<MakerProject> choices) {
        if (busy) { status.setText("요청 처리 중입니다. 목록 새로고침을 눌러 주세요."); return; }
        int pageType = type;
        request(12, "", p -> {
            if (type != pageType || fields.isEmpty() || fields.get(0) != choices) return;
            if (p.code() != Packet.SUCCESS) { status.setText(p.body()); JOptionPane.showMessageDialog(this, "프로젝트 목록 조회 실패: " + p.body() + "\n서버도 최신 코드로 재실행했는지 확인하세요."); return; }
            choices.removeAllItems();
            String[] lines = p.body().split("\n");
            for (int i = 1; i < lines.length; i++) {
                String[] d = lines[i].split("\t", -1);
                if (d.length < 9) continue;
                for (int j = 0; j < d.length; j++) d[j] = unescapeCell(d[j]);

                choices.addItem(new MakerProject(d[0], d[1], d[2], d[3], new String[]{d[1], d[2], d[4], d[5], d[6], d[7]}, d[8]));
            }
            status.setText(choices.getItemCount() == 0 ? "등록된 프로젝트가 없습니다." : "프로젝트 이름을 선택하세요.");
            if (choices.getItemCount() == 0) JOptionPane.showMessageDialog(this, "등록된 프로젝트가 없거나 목록 응답 형식이 다릅니다. 서버 버전과 로그인 계정을 확인하세요.");
        });
    }

    private String makerDisplay(String key, String value) {
        if (value.isBlank() || value.equals("null")) return key.equals("remaining_quantity") || key.equals("total_quantity") ? "무제한" : "—";
        try {
            if (key.equals("target_amount") || key.equals("pledged_amount") || key.equals("funding_amount"))
                return new java.text.DecimalFormat("#,##0.##").format(new java.math.BigDecimal(value)) + "원";
            if (key.equals("achievement_pct")) return value + "%";
        } catch (NumberFormatException ignored) { }
        return value;
    }

    private void renderMakerStatus(String body, JPanel target) {
        var labels = java.util.Map.ofEntries(
                java.util.Map.entry("project_name", "프로젝트명"), java.util.Map.entry("project_status", "진행 상태"),
                java.util.Map.entry("maker", "메이커"), java.util.Map.entry("target_amount", "목표 금액"),
                java.util.Map.entry("pledged_amount", "누적 후원 금액"), java.util.Map.entry("achievement_pct", "달성률"),
                java.util.Map.entry("approval", "심사 상태"), java.util.Map.entry("reason", "심사 사유"),
                java.util.Map.entry("reward_name", "리워드명"), java.util.Map.entry("total_quantity", "전체 수량"),
                java.util.Map.entry("remaining_quantity", "남은 수량"), java.util.Map.entry("pledged_quantity", "후원 수량"),
                java.util.Map.entry("quantity", "수량"), java.util.Map.entry("funding_amount", "후원 금액"),
                java.util.Map.entry("funding_status", "후원 상태"));
        String[] titles = {"프로젝트 요약", "리워드별 현황", "최근 후원 내역 (최대 50건)"};
        String[] blocks = body.split("\n\n");
        for (int b = 0; b < Math.min(blocks.length, titles.length); b++) {
            String[] lines = blocks[b].split("\n");
            String[] keys = lines[0].split("\t", -1);
            JLabel heading = new JLabel(titles[b]);
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
            target.add(heading);
            if (lines.length < 2) { target.add(new JLabel("조회 결과가 없습니다.")); continue; }
            if (b == 0) {
                String[] data = lines[1].split("\t", -1);
                for (int j = 0; j < Math.min(keys.length, data.length); j++) {
                    if (!labels.containsKey(keys[j])) continue;
                    target.add(infoCard(labels.get(keys[j]), makerDisplay(keys[j], data[j])));
                    if (keys[j].equals("achievement_pct")) {
                        JProgressBar progress = new JProgressBar(0, 100);
                        try { progress.setValue(Math.max(0, Math.min(100, new java.math.BigDecimal(data[j]).intValue()))); } catch (NumberFormatException ignored) { }
                        progress.setStringPainted(true); progress.setString(makerDisplay(keys[j], data[j]));
                        target.add(progress);
                    }
                }
            } else {
                var indices = new ArrayList<Integer>();
                var columns = new ArrayList<String>();
                for (int j = 0; j < keys.length; j++) if (labels.containsKey(keys[j])) { indices.add(j); columns.add(labels.get(keys[j])); }
                var model = new DefaultTableModel(columns.toArray(), 0) { public boolean isCellEditable(int r, int c) { return false; } };
                for (int i = 1; i < lines.length; i++) {
                    String[] data = lines[i].split("\t", -1);
                    var values = new ArrayList<String>();
                    for (int j : indices) values.add(makerDisplay(keys[j], j < data.length ? data[j] : ""));
                    model.addRow(values.toArray());
                }
                JTable table = new JTable(model); table.setRowHeight(30); table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                for (int j = 0; j < table.getColumnCount(); j++) table.getColumnModel().getColumn(j).setPreferredWidth(115);
                JScrollPane scroll = new JScrollPane(table); scroll.setPreferredSize(new Dimension(450, Math.min(220, 55 + 30 * model.getRowCount()))); target.add(scroll);
            }
            target.add(Box.createVerticalStrut(12));
        }
    }

    private void render(Packet p, int t) {
        render(p, t, output);
    }

    private void render(Packet p, int t, JPanel output) {
        output.removeAll();

        boolean ok = p.code() == Packet.SUCCESS;
        status.setText(ok ? "요청 완료" : "요청 실패 · " + String.format("CODE 0x%04X", p.code()));

        if (ok && t == 12) {
            renderMakerStatus(p.body(), output);
        } else if (ok && (t == 3 || t == 10 || t == 14 || t == 15)) {
            for (String block : p.body().split("\n\n")) {
                String[] lines = block.split("\n");

                var model = new DefaultTableModel(lines[0].split("\t", -1), 0) {
                    @Override
                    public boolean isCellEditable(int r, int c) {
                        return false;
                    }
                };

                for (int i = 1; i < lines.length; i++) {
                    model.addRow(lines[i].split("\t", -1));
                }

                JTable table = new JTable(model);
                table.setRowHeight(25);
                table.setAutoCreateRowSorter(true);
                table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

                for (int j = 0; j < table.getColumnCount(); j++) {
                    table.getColumnModel().getColumn(j).setPreferredWidth(
                            Math.max(115, table.getColumnName(j).length() * 10)
                    );
                }

                JScrollPane panel = new JScrollPane(table);
                panel.setPreferredSize(new Dimension(570, Math.min(220, 58 + 25 * model.getRowCount())));

                output.add(panel);

                if (model.getRowCount() == 0) {
                    output.add(new JLabel("조회 결과가 없습니다."));
                }

                output.add(Box.createVerticalStrut(8));
            }
        } else {
            String message = ok ? switch (t) {
                case 1 -> name + "님, 로그인되었습니다.";
                case 2 -> "등록된 프로젝트 ID: " + p.body();
                case 4 -> "후원 완료 · 금액: " + p.body();
                case 19 -> "개인정보가 성공적으로 수정되었습니다.";
                default -> p.body();
            } : p.body();

            JTextArea a = new JTextArea(message);
            a.setLineWrap(true);
            a.setWrapStyleWord(true);
            a.setEditable(false);
            a.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            output.add(a);
        }

        output.revalidate();
        output.repaint();
    }
}