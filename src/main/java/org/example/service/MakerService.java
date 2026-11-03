package org.example.service;

import org.example.persistence.DBConnection;
import org.example.common.AppSupport;
import org.example.common.BusinessException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.example.common.AppSupport.*;

public class MakerService {
    public static void test01ViewFundingStatus() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            long makerId = findUserId(conn, MAKER_ID);

            // 1. 프로젝트별 총 펀딩 금액
            String projectSql = """
                    SELECT p.project_id, p.project_name, p.category,
                           p.target_amount, p.project_status,
                           COALESCE(SUM(f.funding_amount), 0) AS total_funding,
                           COUNT(f.funding_id) AS supporter_count
                    FROM project p
                    LEFT JOIN funding f
                        ON p.project_id = f.project_id
                       AND f.funding_status = '후원완료'
                    WHERE p.maker_id = ?
                    GROUP BY p.project_id, p.project_name, p.category,
                             p.target_amount, p.project_status
                    ORDER BY p.project_id
                    """;

            // 2. 리워드별 신청 수량 / 잔여 수량
            String rewardSql = """
                    SELECT r.reward_id, r.reward_name, r.total_quantity,
                           r.remaining_quantity,
                           COALESCE(SUM(f.quantity), 0) AS applied_quantity
                    FROM reward r
                    LEFT JOIN funding f
                        ON r.reward_id = f.reward_id
                       AND f.funding_status = '후원완료'
                    WHERE r.project_id = ?
                    GROUP BY r.reward_id, r.reward_name,
                             r.total_quantity, r.remaining_quantity
                    ORDER BY r.reward_id
                    """;

            // 3. 리워드별 서포터와 배송지
            String supporterSql = """
                    SELECT u.name, f.quantity,
                           a.postal_code, a.base_address, a.detail_address
                    FROM funding f
                    JOIN users u ON f.supporter_id = u.id
                    LEFT JOIN shipping_address a ON f.address_id = a.address_id
                    WHERE f.reward_id = ?
                      AND f.funding_status = '후원완료'
                    ORDER BY f.funding_id
                    """;

            try (PreparedStatement ps = conn.prepareStatement(projectSql);
                 PreparedStatement rewardPs = conn.prepareStatement(rewardSql);
                 PreparedStatement supporterPs = conn.prepareStatement(supporterSql)) {

                ps.setLong(1, makerId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int projectId = rs.getInt("project_id");

                        System.out.println("\n==================================================");
                        System.out.printf("[%d] %s (%s, %s)%n", projectId,
                                rs.getString("project_name"),
                                rs.getString("category"),
                                rs.getString("project_status"));
                        System.out.printf("총 펀딩 금액: %,.0f원 / 목표 %,.0f원 (후원 %d건)%n",
                                rs.getBigDecimal("total_funding"),
                                rs.getBigDecimal("target_amount"),
                                rs.getInt("supporter_count"));

                        rewardPs.setInt(1, projectId);

                        try (ResultSet rr = rewardPs.executeQuery()) {
                            while (rr.next()) {
                                Object remaining = rr.getObject("remaining_quantity");
                                String remainingText = remaining == null
                                        ? "제한 없음" : remaining + "개";

                                System.out.printf("  - 리워드 [%d] %s | 신청 수량 %d개 | 잔여 수량 %s%n",
                                        rr.getInt("reward_id"),
                                        rr.getString("reward_name"),
                                        rr.getInt("applied_quantity"),
                                        remainingText);

                                supporterPs.setInt(1, rr.getInt("reward_id"));

                                try (ResultSet sr = supporterPs.executeQuery()) {
                                    while (sr.next()) {
                                        System.out.printf("      %s (%d개) | (%s) %s %s%n",
                                                sr.getString("name"),
                                                sr.getInt("quantity"),
                                                sr.getString("postal_code"),
                                                sr.getString("base_address"),
                                                sr.getString("detail_address"));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void test02RegisterProject() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            long makerId = findUserId(conn, MAKER_ID);

            String name = readLine("프로젝트명: ");
            if (name.isBlank() || name.length() > 200) {
                throw new BusinessException("프로젝트명은 1~200자로 입력하세요.");
            }

            String category = selectCategory(false);
            String description = readLine("설명: ");
            BigDecimal target = readAmount("목표 금액: ");

            int days = readInt("진행 기간(일): ");
            if (days < 1) {
                throw new BusinessException("진행 기간은 1일 이상이어야 합니다.");
            }

            String sql = """
                    INSERT INTO project
                    (maker_id, project_name, category, description,
                     target_amount, start_date, end_date, project_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, '대기')
                    """;

            try (PreparedStatement ps = conn.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, makerId);
                ps.setString(2, name);
                ps.setString(3, category);
                ps.setString(4, description);
                ps.setBigDecimal(5, target);
                ps.setDate(6, Date.valueOf(LocalDate.now()));
                ps.setDate(7, Date.valueOf(LocalDate.now().plusDays(days)));
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    System.out.println("프로젝트 등록 완료. 프로젝트 ID: " + rs.getInt(1));
                    System.out.println("리워드를 등록한 뒤 승인 신청을 하세요. (test03)");
                }
            }
        }
    }

    public static void test03RegisterReward() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            long makerId = findUserId(conn, MAKER_ID);

            // 1. 승인 전('대기') 프로젝트 목록 출력
            System.out.println("\n===== 승인 전 내 프로젝트 목록 =====");

            String projectSql = """
                    SELECT p.project_id, p.project_name, p.category,
                           p.target_amount,
                           (SELECT COUNT(*) FROM reward r
                            WHERE r.project_id = p.project_id) AS reward_count
                    FROM project p
                    WHERE p.maker_id = ?
                      AND p.project_status = '대기'
                    ORDER BY p.project_id
                    """;

            List<Integer> projectIds = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(projectSql)) {
                ps.setLong(1, makerId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        projectIds.add(rs.getInt("project_id"));

                        System.out.println("프로젝트 ID: " + rs.getInt("project_id")
                                + " | " + rs.getString("project_name")
                                + " | " + rs.getString("category")
                                + String.format(" | 목표 %,.0f원", rs.getBigDecimal("target_amount"))
                                + " | 등록된 리워드 " + rs.getInt("reward_count") + "개");
                    }
                }
            }

            if (projectIds.isEmpty()) {
                System.out.println("승인 전 프로젝트가 없습니다. test02로 먼저 등록하세요.");
                return;
            }

            int projectId = readInt("\n리워드를 등록할 프로젝트 ID: ");

            if (!projectIds.contains(projectId)) {
                throw new BusinessException("목록에 없는 프로젝트 ID입니다.");
            }

            // 2. 리워드 입력 루프 (실수해도 기존 입력 데이터가 유지되도록 예외 방어 적용)
            List<String[]> rewards = new ArrayList<>();

            while (true) {
                System.out.println("\n--- 리워드 " + (rewards.size() + 1)
                        + " (리워드명을 비우면 입력 종료) ---");

                String name = readLine("리워드명: ");
                if (name.isBlank()) {
                    break; // 리워드명이 공백이면 입력 종료 및 저장 단계로 이동
                }

                String description = readLine("리워드 설명: ");
                if (description.isBlank()) {
                    System.out.println("⚠️ [오류] 리워드 설명을 입력하세요. (이전까지 입력한 리워드는 유지됩니다.)");
                    continue; // 현재 리워드만 다시 입력하도록 유도
                }

                BigDecimal amount;
                try {
                    amount = readAmount("필요 후원 금액: ");
                } catch (Exception e) {
                    System.out.println("⚠️ [오류] 올바른 금액을 입력하세요. (이전까지 입력한 리워드는 유지됩니다.)");
                    continue;
                }

                int quantity;
                try {
                    quantity = readInt("총 수량 (무제한은 0): ");
                    if (quantity < 0) {
                        System.out.println("⚠️ [오류] 수량은 0 이상이어야 합니다. (이전까지 입력한 리워드는 유지됩니다.)");
                        continue;
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ [오류] 올바른 숫자를 입력하세요. (이전까지 입력한 리워드는 유지됩니다.)");
                    continue;
                }

                // 정상 입력된 경우에만 리스트에 추가
                rewards.add(new String[]{
                        name, description, amount.toPlainString(),
                        String.valueOf(quantity)});

                System.out.println("✅ 리워드가 임시 추가되었습니다. (현재 총 " + rewards.size() + "개)");
            }

            if (rewards.isEmpty()) {
                System.out.println("등록할 리워드가 없습니다.");
                return;
            }

            // 3. 리워드 저장 + 승인 신청 (DB 트랜잭션 시작)
            conn.setAutoCommit(false);

            try {
                for (String[] reward : rewards) {
                    int quantity = Integer.parseInt(reward[3]);
                    Integer limit = quantity == 0 ? null : quantity;

                    insertReward(conn, projectId, reward[0], reward[1],
                            reward[2], limit, limit);
                }

                int rewardCount = countRows(conn,
                        "SELECT COUNT(*) FROM reward WHERE project_id = ?",
                        projectId);

                String lastAction = findLastApprovalAction(conn, projectId);

                if (rewardCount < 2) {
                    conn.commit();
                    System.out.println("리워드 등록 완료. 리워드가 2개 이상이어야 승인 신청이 됩니다. (현재 "
                            + rewardCount + "개)");
                    return;
                }

                if ("신청".equals(lastAction) || "재신청".equals(lastAction)) {
                    conn.commit();
                    System.out.println("리워드 등록 완료. 이미 승인 신청된 프로젝트입니다.");
                    return;
                }

                // 거절된 적이 있으면 재신청
                String action = "거절".equals(lastAction) ? "재신청" : "신청";
                insertApproval(conn, projectId, makerId, action);

                conn.commit();
                System.out.println("리워드 " + rewards.size() + "개 등록 및 승인 " + action + " 완료.");

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static String findLastApprovalAction(Connection conn, int projectId)
            throws SQLException {
        String sql = """
                SELECT action
                FROM approval_history
                WHERE project_id = ?
                ORDER BY history_id DESC
                LIMIT 1
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("action") : null;
            }
        }
    }
}
