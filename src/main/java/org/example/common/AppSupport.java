package org.example.common;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Scanner;

public class AppSupport {
    private static final Scanner sc = new Scanner(System.in);
    public static final String MAKER_ID = "test_maker";
    public static final String MAKER2_ID = "test_maker2";
    public static final String SUPPORTER_ID = "test_supporter";
    public static final String ADMIN_ID = "test_admin";
    public static final String[] CATEGORIES = {"가전", "식품"};
    public static final String PUBLIC_STATUS = "('진행중', '성공', '실패', '종료')";
    public static final String CLOSED_STATUS = "('성공', '실패', '종료')";

    public static long findUserId(Connection conn, String loginId)
            throws SQLException {
        String sql = "SELECT id FROM users WHERE login_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loginId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "테스트 회원을 찾을 수 없습니다: " + loginId);
                }

                return rs.getLong("id");
            }
        }
    }

    public static boolean exists(Connection conn, String sql, Object... values)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static int countRows(Connection conn, String sql, Object... values)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static String readLine(String prompt) {
        System.out.print(prompt);
        return sc.nextLine().trim();
    }

    public static int readInt(String prompt) throws BusinessException {
        String input = readLine(prompt);

        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new BusinessException("숫자를 입력해야 합니다: " + input);
        }
    }

    public static BigDecimal readAmount(String prompt) throws BusinessException {
        String input = readLine(prompt);

        try {
            BigDecimal amount = new BigDecimal(input);

            if (amount.signum() <= 0 || amount.scale() > 0) {
                throw new BusinessException("금액은 1원 이상의 정수로 입력하세요.");
            }

            return amount;
        } catch (NumberFormatException e) {
            throw new BusinessException("금액은 숫자로 입력해야 합니다: " + input);
        }
    }

    public static String selectCategory(boolean allowAll) throws BusinessException {
        System.out.println("카테고리를 선택하세요:");

        for (int i = 0; i < CATEGORIES.length; i++) {
            System.out.println((i + 1) + ". " + CATEGORIES[i]);
        }

        String input = readLine(allowAll ? "선택 (전체는 Enter): " : "선택 (1~2): ");

        if (allowAll && input.isEmpty()) {
            return null;
        }

        if ("1".equals(input)) {
            return CATEGORIES[0];
        } else if ("2".equals(input)) {
            return CATEGORIES[1];
        }

        throw new BusinessException("올바른 카테고리 번호를 선택하세요.");
    }

    public static int insertReward(
            Connection conn, int projectId, String name, String description,
            String requiredAmount, Integer totalQuantity,
            Integer remainingQuantity) throws SQLException {

        String sql = """
                INSERT INTO reward
                (project_id, reward_name, description,
                 required_amount, total_quantity, remaining_quantity)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, projectId);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.setBigDecimal(4, new BigDecimal(requiredAmount));

            if (totalQuantity == null) {
                ps.setNull(5, Types.INTEGER);
                ps.setNull(6, Types.INTEGER);
            } else {
                ps.setInt(5, totalQuantity);
                ps.setInt(6, remainingQuantity);
            }

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("리워드 ID를 가져오지 못했습니다.");
                }
                return rs.getInt(1);
            }
        }
    }

    public static void insertApproval(
            Connection conn, int projectId, long actorId, String action)
            throws SQLException {

        String sql = """
                INSERT INTO approval_history
                (project_id, actor_id, action, reason)
                VALUES (?, ?, ?, NULL)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            ps.setLong(2, actorId);
            ps.setString(3, action);
            ps.executeUpdate();
        }
    }
}
