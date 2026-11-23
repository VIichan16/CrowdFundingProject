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
            new String[]{"번호", "프로젝트", "카테고리", "상태"}, 0
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
            if (!busy) page(type == 1 ? 11 : 1);
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

        JPanel actions = new JPanel(new GridLayout(0, 2, 4, 4));
        JButton refreshBtn = new JButton("새로고침");
        JButton detail = new JButton("상세·리워드 사진");

        actions.add(refreshBtn);
        actions.add(pending);
        actions.add(detail);
        actions.add(approve);

        JPanel filterBar = new JPanel(new GridLayout(2, 2, 4, 4));
        filterBar.add(categoryFilter);
        filterBar.add(statusFilter);
        filterBar.add(keywordFilter);
        filterBar.add(sortOption);

        JPanel catalogTop = new JPanel(new BorderLayout(0, 4));
        catalogTop.add(actions, BorderLayout.NORTH);
        catalogTop.add(filterBar, BorderLayout.SOUTH);

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
        catalog.setFont(catalog.getFont().deriveFont(14f));
        catalog.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        catalog.getColumnModel().getColumn(0).setPreferredWidth(48);
        catalog.getColumnModel().getColumn(1).setPreferredWidth(340);

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
        page(1);
        refresh();
        applyCatalogFilterAndSort();
    }

    private void syncCategoryFilterOptions() {
        String current = String.valueOf(categoryFilter.getSelectedItem());
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        categories.add("전체 카테고리");
        for (int r = 0; r < catalogModel.getRowCount(); r++) {
            String cat = String.valueOf(catalogModel.getValueAt(r, 2)).trim();
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
                String title = entry.getStringValue(1).toLowerCase(Locale.ROOT);
                String category = entry.getStringValue(2).trim();
                String statusText = entry.getStringValue(3).trim();

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
            case 3 -> keys.add(new RowSorter.SortKey(2, SortOrder.ASCENDING));
            case 4 -> keys.add(new RowSorter.SortKey(3, SortOrder.ASCENDING));
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
            if (t == 15) openMyPageDialog();
            else page(t);
        });

        if (!role.isEmpty()) {
            button("로그아웃", () -> {
                disconnect();
                role = "";
                name = "";
                menu();
                page(1);
                refresh();
            });
        }

        nav.revalidate();
        nav.repaint();
    }

    private void button(String title, Runnable action) {
        JButton b = new JButton(title);
        b.setMaximumSize(new Dimension(170, 42));
        b.setPreferredSize(new Dimension(148, 40));
        b.setFont(b.getFont().deriveFont(Font.BOLD, 14f));
        b.setFocusPainted(false);
        b.setBackground(Color.WHITE);
        b.setForeground(new Color(47, 58, 70));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 228, 234)),
                BorderFactory.createEmptyBorder(7, 15, 7, 15)));
        b.addActionListener(e -> {
            if (!busy) action.run();
        });
        nav.add(b);
    }

    private void row(String label, JComponent component) {
        JPanel r = new JPanel(new BorderLayout(10, 0));
        r.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(role.isEmpty() ? 90 : 155, 26));

        r.add(l, BorderLayout.WEST);
        r.add(component);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
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


    /** 마이페이지를 작은 우측 작업창이 아닌 독립된 큰 팝업 화면으로 표시한다. */
    private void openMyPageDialog() {
        if (role.isEmpty()) {
            JOptionPane.showMessageDialog(this, "로그인 후 이용할 수 있습니다.", "마이페이지", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "마이페이지", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(920, 680);
        dialog.setMinimumSize(new Dimension(760, 560));
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
        JButton close = new JButton("닫기");
        close.addActionListener(e -> dialog.dispose());
        header.add(close, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(tabs.getFont().deriveFont(Font.PLAIN, 15f));
        root.add(tabs, BorderLayout.CENTER);

        // 후원 내역 탭
        JPanel historyPanel = new JPanel(new BorderLayout(10, 10));
        historyPanel.setBorder(BorderFactory.createEmptyBorder(18, 12, 12, 12));
        JLabel historyTitle = new JLabel("내가 후원한 프로젝트");
        historyTitle.setFont(historyTitle.getFont().deriveFont(Font.BOLD, 19f));
        historyPanel.add(historyTitle, BorderLayout.NORTH);
        DefaultTableModel historyModel = new DefaultTableModel(new Object[]{"후원 내역"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable historyTable = new JTable(historyModel);
        historyTable.setRowHeight(38);
        historyTable.setFont(historyTable.getFont().deriveFont(Font.PLAIN, 14f));
        historyTable.getTableHeader().setFont(historyTable.getTableHeader().getFont().deriveFont(Font.BOLD, 14f));
        historyTable.getTableHeader().setReorderingAllowed(false);
        historyTable.setAutoCreateRowSorter(true);
        historyTable.setFillsViewportHeight(true);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.setGridColor(new Color(230, 233, 238));
        historyTable.setShowVerticalLines(false);
        historyTable.setIntercellSpacing(new Dimension(0, 1));
        historyPanel.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        JButton writeReview = new JButton("선택한 프로젝트 리뷰 작성");
        writeReview.setEnabled(false);
        historyTable.getSelectionModel().addListSelectionListener(e ->
                writeReview.setEnabled(!e.getValueIsAdjusting() && historyTable.getSelectedRow() >= 0));
        writeReview.addActionListener(e -> {
            int selectedViewRow = historyTable.getSelectedRow();
            if (selectedViewRow < 0) return;
            int selectedModelRow = historyTable.convertRowIndexToModel(selectedViewRow);
            int idColumn = historyModel.getColumnCount() - 1;
            String projectId = String.valueOf(historyModel.getValueAt(selectedModelRow, idColumn));
            openReviewDialog(dialog, projectId);
        });
        JPanel historyBottom = new JPanel(new BorderLayout(8, 8));
        JLabel historyStatus = new JLabel("후원 내역을 불러오는 중입니다...");
        historyBottom.add(historyStatus, BorderLayout.CENTER);
        historyBottom.add(writeReview, BorderLayout.EAST);
        historyPanel.add(historyBottom, BorderLayout.SOUTH);
        tabs.addTab("내 후원 내역", historyPanel);

        // 개인정보 탭
        JPanel profileOuter = new JPanel(new BorderLayout(10, 10));
        profileOuter.setBorder(BorderFactory.createEmptyBorder(18, 12, 12, 12));
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
        JLabel passwordHint = new JLabel("비밀번호를 변경하지 않으려면 비밀번호 입력란을 비워 두세요.");
        passwordHint.setForeground(new Color(110, 110, 110));
        JButton save = new JButton("변경사항 저장");
        save.setPreferredSize(new Dimension(150, 40));
        JPanel profileBottom = new JPanel(new BorderLayout(8, 8));
        profileBottom.add(passwordHint, BorderLayout.CENTER);
        profileBottom.add(save, BorderLayout.EAST);
        profileOuter.add(new JScrollPane(profileForm), BorderLayout.CENTER);
        profileOuter.add(profileBottom, BorderLayout.SOUTH);
        tabs.addTab("개인정보 수정", profileOuter);

        save.addActionListener(e -> {
            if (busy) return;
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
                    currentPw.setText(""); newPw.setText(""); confirmPw.setText("");
                    JOptionPane.showMessageDialog(dialog, "개인정보가 수정되었습니다.", "저장 완료", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(dialog, result.body(), "수정 실패", JOptionPane.ERROR_MESSAGE);
                }
            });
        });

        dialog.setVisible(true);

        // 서버가 반환하는 기존 회원정보 형식과 후원내역 형식을 그대로 사용한다.
        request(15, "", profile -> {
            if (profile.code() != Packet.SUCCESS) {
                JOptionPane.showMessageDialog(dialog, "회원정보 조회 실패: " + profile.body());
                return;
            }
            String[] lines = profile.body().split("\n", -1);
            if (lines.length >= 2) {
                String[] data = lines[1].split("\t", -1);
                if (data.length >= 6) {
                    userId.setText(data[0]);
                    userName.setText(data[2]);
                    phone.setText(data[4]);
                    bio.setText(data[5].equals("null") ? "" : data[5]);
                }
            }
            if (role.equals("SUPPORTER")) {
                request(10, "", history -> {
                    historyModel.setRowCount(0);
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
                    if (rows.length > 0) {
                        String[] headers = rows[0].split("\t", -1);
                        historyModel.setColumnIdentifiers(headers);
                        for (int i = 1; i < rows.length; i++) {
                            if (!rows[i].isBlank()) {
                                String[] cells = rows[i].split("\t", -1);
                                // 금액은 천 단위 구분 기호를 적용해 읽기 쉽게 표시한다.
                                if (cells.length > 3) {
                                    try {
                                        java.math.BigDecimal amount = new java.math.BigDecimal(cells[3]);
                                        cells[3] = String.format(java.util.Locale.KOREA, "%,.0f원", amount);
                                    } catch (NumberFormatException ignored) { }
                                }
                                historyModel.addRow(cells);
                            }
                        }
                    }
                    if (historyTable.getColumnModel().getColumnCount() == 7) {
                        // 프로젝트 ID는 리뷰 작성에만 사용하고 화면에서는 숨긴다.
                        historyTable.removeColumn(historyTable.getColumnModel().getColumn(6));
                    }
                    if (historyTable.getColumnModel().getColumnCount() == 6) {
                        int[] widths = {280, 220, 85, 145, 170, 100};
                        for (int i = 0; i < widths.length; i++)
                            historyTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
                    }
                    historyStatus.setText(historyModel.getRowCount() == 0 ? "후원 내역이 없습니다." : "총 " + historyModel.getRowCount() + "건의 후원 내역");
                });
            } else {
                historyStatus.setText("후원 내역은 서포터 계정에서 확인할 수 있습니다.");
            }
        });
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
        String title = String.valueOf(catalogModel.getValueAt(modelRow, 1));
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

        entrySwitch.setVisible(t == 1 || t == 11);
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

                    if (icon.getIconWidth() <= 0) {
                        throw new IOException("사진 파일을 읽을 수 없습니다.");
                    }

                    projectImage = java.util.Base64.getEncoder().encodeToString(bytes);
                    choose.setText(picker.getSelectedFile().getName());
                    status.setText("대표 사진 선택 완료");

                } catch (IOException ex) {
                    projectImage = null;
                    choose.setText("대표 사진 선택 (JPG/PNG)");
                    status.setText(ex.getMessage());
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "사진 첨부 실패", JOptionPane.ERROR_MESSAGE);
                }
            });

            row("대표 사진", choose);
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

        // 프로젝트 메인 화면에서는 오른쪽 '프로젝트 둘러보기' 패널을 완전히 제거하고
        // 프로젝트 목록/검색 영역을 전체 너비로 사용한다. 다른 기능 화면에서는 기존 분할 레이아웃을 복원한다.
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

        if (t == 14 && values.get(0).isBlank()) values.clear();

        for (String s : values) {
            if (s.indexOf('\t') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
                status.setText("탭과 줄바꿈은 사용할 수 없어요.");
                return;
            }
        }

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

            render(p, requestType);

            if (t == 1 || (p.code() == Packet.SUCCESS && (t == 2 || t == 7 || t == 9 || t == 13))) {
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
                    page(1);
                    status.setText("연결 오류: 서버 실행 상태와 포트를 확인한 뒤 다시 로그인하세요.");
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
                catalogModel.addRow(lines[i].split("\t", -1));
            }

            syncCategoryFilterOptions();
            applyCatalogFilterAndSort();
            status.setText("프로젝트 " + catalog.getRowCount() + "개 표시 (전체 " + catalogModel.getRowCount() + "개)");
            if (type == 0 && catalog.getRowCount() > 0 && catalog.getSelectedRow() < 0) catalog.setRowSelectionInterval(0, 0);
            updateHomePreview();
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
        String titleText = String.valueOf(catalogModel.getValueAt(modelRow, 1));

        request(3, id, p -> {
            if (p.code() != Packet.SUCCESS) {
                render(p, 3);
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

            JPanel root = new JPanel(new BorderLayout(28, 0));
            root.setBackground(Color.WHITE);
            root.setBorder(BorderFactory.createEmptyBorder(26, 28, 26, 28));

            JPanel left = new JPanel(new BorderLayout(0, 12));
            left.setBackground(Color.WHITE);
            left.setPreferredSize(new Dimension(420, 0));

            JLabel hero = new JLabel("대표 이미지를 불러오는 중", SwingConstants.CENTER);
            hero.setOpaque(true);
            hero.setBackground(soft);
            hero.setForeground(muted);
            hero.setBorder(BorderFactory.createLineBorder(line));
            hero.setPreferredSize(new Dimension(420, 420));
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
            title.setFont(title.getFont().deriveFont(Font.BOLD, 29f));
            summary.add(title);
            summary.add(Box.createVerticalStrut(12));

            JButton likeButton = new JButton("♡ 좋아요");
            likeButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            likeButton.setEnabled("SUPPORTER".equals(role));
            likeButton.setToolTipText("서포터 계정으로 로그인하면 좋아요를 누를 수 있습니다.");
            likeButton.addActionListener(likeEvent -> {
                if (!"SUPPORTER".equals(role)) {
                    JOptionPane.showMessageDialog(dialog, "좋아요는 서포터 계정으로 로그인한 후 이용할 수 있습니다.");
                    return;
                }
                likeButton.setEnabled(false);
                request(5, id, likeResult -> {
                    if (likeResult.code() == Packet.SUCCESS) {
                        likeButton.setText("♥ 좋아요 완료");
                        JOptionPane.showMessageDialog(dialog, "이 프로젝트에 좋아요를 눌렀습니다.");
                    } else {
                        likeButton.setEnabled(true);
                        JOptionPane.showMessageDialog(dialog, likeResult.body(), "좋아요 처리", JOptionPane.INFORMATION_MESSAGE);
                    }
                });
            });
            summary.add(likeButton);
            summary.add(Box.createVerticalStrut(16));

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
                fundingAmount.setFont(fundingAmount.getFont().deriveFont(Font.BOLD, 22f));
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

                JLabel percentLabel = new JLabel(String.format(Locale.KOREA, "목표 달성률 %.2f%%", pct));
                percentLabel.setForeground(accent);
                percentLabel.setFont(percentLabel.getFont().deriveFont(Font.BOLD, 13f));
                fundingBox.add(percentLabel);

                summary.add(fundingBox);
                summary.add(Box.createVerticalStrut(13));

                JTextArea description = new JTextArea(data[3].replace("\\n", "\n"));
                description.setEditable(false);
                description.setLineWrap(true);
                description.setWrapStyleWord(true);
                description.setFont(description.getFont().deriveFont(13f));
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
            rewardTitle.setFont(rewardTitle.getFont().deriveFont(Font.BOLD, 19f));
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

            if (blocks.length == 2) {
                String[] lines = blocks[1].split("\n");

                for (int i = 1; i < lines.length; i++) {
                    String[] r = lines[i].split("\t", -1);
                    if (r.length < 5) continue;

                    JPanel card = new JPanel(new BorderLayout(14, 0));
                    card.setBackground(Color.WHITE);
                    card.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(line),
                            BorderFactory.createEmptyBorder(12, 14, 12, 14)
                    ));
                    card.setPreferredSize(new Dimension(650, 142));
                    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 142));
                    card.setAlignmentX(Component.LEFT_ALIGNMENT);

                    JPanel details = new JPanel();
                    details.setBackground(Color.WHITE);
                    details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));

                    JLabel rewardName = new JLabel(escapeHtml(r[1]));
                    rewardName.setForeground(ink);
                    rewardName.setFont(rewardName.getFont().deriveFont(Font.BOLD, 15f));
                    details.add(rewardName);
                    details.add(Box.createVerticalStrut(7));

                    JLabel price = new JLabel(formatMoney(r[3]) + "원  ·  " + (r[4].isBlank() ? "수량 제한 없음" : "잔여 " + r[4] + "개"));
                    price.setForeground(accent);
                    price.setFont(price.getFont().deriveFont(Font.BOLD, 13f));
                    details.add(price);

                    if (!r[2].isBlank()) {
                        details.add(Box.createVerticalStrut(5));
                        JLabel desc = new JLabel("<html><div style='width:390px;color:#777777'>" + escapeHtml(r[2]) + "</div></html>");
                        desc.setFont(desc.getFont().deriveFont(12f));
                        details.add(desc);
                    }

                    // 리워드별 후원 기능은 프로젝트 상세 화면 안에서 제공한다.
                    JPanel rewardActions = new JPanel();
                    rewardActions.setOpaque(false);
                    rewardActions.setLayout(new BoxLayout(rewardActions, BoxLayout.Y_AXIS));
                    JButton fundButton = new JButton("이 리워드 후원하기");
                    fundButton.setEnabled(!role.isEmpty());
                    fundButton.setToolTipText(role.isEmpty() ? "후원하려면 먼저 로그인하세요." : "선택한 리워드를 후원합니다.");
                    fundButton.addActionListener(ev -> {
                        if (!"SUPPORTER".equals(role)) {
                            JOptionPane.showMessageDialog(dialog,
                                    role.isEmpty() ? "리워드 후원은 로그인 후 이용할 수 있습니다." : "후원자 계정으로 로그인해야 합니다.",
                                    "로그인 필요", JOptionPane.INFORMATION_MESSAGE);
                            return;
                        }
                        String quantityText = JOptionPane.showInputDialog(dialog, "후원 수량을 입력하세요.", "1");
                        if (quantityText == null) return;
                        int quantity;
                        try { quantity = Integer.parseInt(quantityText.trim()); }
                        catch (NumberFormatException ex) {
                            JOptionPane.showMessageDialog(dialog, "수량은 숫자로 입력하세요."); return;
                        }
                        if (quantity <= 0) {
                            JOptionPane.showMessageDialog(dialog, "수량은 1개 이상이어야 합니다."); return;
                        }
                        fundButton.setEnabled(false);
                        request(4, id + "\t" + r[0] + "\t" + quantity, result -> {
                            fundButton.setEnabled(true);
                            if (result.code() == Packet.SUCCESS) {
                                JOptionPane.showMessageDialog(dialog,
                                        "후원이 완료되었습니다.\n결제 금액: " + result.body() + "원",
                                        "후원 완료", JOptionPane.INFORMATION_MESSAGE);
                                dialog.dispose();
                                refresh();
                            } else {
                                JOptionPane.showMessageDialog(dialog, result.body(), "후원 실패", JOptionPane.WARNING_MESSAGE);
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
                    photo.setPreferredSize(new Dimension(116, 116));
                    card.add(photo, BorderLayout.EAST);
                    rewardPhotos.add(photo);

                    rewards.add(card);
                    rewards.add(Box.createVerticalStrut(9));
                }
            }

            JScrollPane rewardScroll = new JScrollPane(rewards);
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

            request(18, id, res -> {
                if (!dialog.isDisplayable()) return;

                if (res.code() != Packet.SUCCESS) {
                    hero.setText("대표 이미지를 불러오지 못했습니다.");
                } else {
                    try {
                        ImageIcon icon = new ImageIcon(java.util.Base64.getDecoder().decode(res.body()));
                        if (icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
                            hero.setText("");
                            hero.setIcon(scaled(icon, 410, 410));
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
    }

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
                        photo.setIcon(scaled(icon, 110, 110));
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

    private void render(Packet p, int t) {
        render(p, t, output);
    }

    private void render(Packet p, int t, JPanel output) {
        output.removeAll();

        boolean ok = p.code() == Packet.SUCCESS;
        status.setText(ok ? "요청 완료" : "요청 실패 · " + String.format("CODE 0x%04X", p.code()));

        if (ok && (t == 3 || t == 10 || t == 12 || t == 14 || t == 15)) {
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