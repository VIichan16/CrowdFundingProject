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

public class FundingService {
    public static void test08ApplyReward() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            long supporterId = findUserId(conn, SUPPORTER_ID);

            printOpenRewards(conn);

            int projectId = readInt("\n프로젝트 ID: ");
            int rewardId = readInt("리워드 ID: ");
            int quantity = readInt("수량: ");

            BigDecimal amount = applyReward(conn, supporterId, projectId, rewardId, quantity);

            System.out.println("리워드 신청 완료. 후원 금액: " + String.format("%,.0f원", amount));
        }
    }

    public static void printOpenRewards(Connection conn) throws SQLException {
        String sql = """
                SELECT p.project_id, p.project_name,
                       r.reward_id, r.reward_name, r.required_amount,
                       r.remaining_quantity
                FROM project p
                JOIN reward r ON p.project_id = r.project_id
                WHERE p.project_status = '진행중'
                ORDER BY p.project_id, r.reward_id
                """;

        System.out.println("\n========== 신청 가능한 리워드 ==========");

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Object remaining = rs.getObject("remaining_quantity");

                System.out.printf("프로젝트 %d %s | 리워드 %d %s | %,.0f원 | 잔여 %s%n",
                        rs.getInt("project_id"),
                        rs.getString("project_name"),
                        rs.getInt("reward_id"),
                        rs.getString("reward_name"),
                        rs.getBigDecimal("required_amount"),
                        remaining == null ? "제한 없음" : remaining + "개");
            }
        }
    }

    public static BigDecimal applyReward(
            Connection conn,
            long supporterId,
            int projectId,
            int rewardId,
            int quantity
    ) throws Exception {

        if (quantity <= 0) {
            throw new BusinessException("수량은 1 이상이어야 합니다.");
        }

        conn.setAutoCommit(false);

        try {
            // 1. 프로젝트 행 잠금 및 신청 가능 여부 확인
            String projectSql = """
                SELECT project_id
                FROM project
                WHERE project_id = ?
                  AND project_status = '진행중'
                  AND end_date >= CURRENT_DATE
                FOR UPDATE
                """;

            if (!exists(conn, projectSql, projectId)) {
                throw new BusinessException(
                        "후원 가능한 진행 중 프로젝트가 아닙니다."
                );
            }

            // 2. 동일 프로젝트의 동일 리워드 중복 신청만 방지
            // 서로 다른 reward_id는 같은 프로젝트에서도 신청 가능
            String duplicateSql = """
                SELECT 1
                FROM funding
                WHERE supporter_id = ?
                  AND project_id = ?
                  AND reward_id = ?
                  AND funding_status = '후원완료'
                """;

            if (exists(
                    conn,
                    duplicateSql,
                    supporterId,
                    projectId,
                    rewardId
            )) {
                throw new BusinessException(
                        "이미 이 프로젝트의 해당 리워드를 신청했습니다."
                );
            }

            // 3. 리워드 행 잠금 및 금액·재고 확인
            BigDecimal amount;
            Integer remaining;

            String rewardSql = """
                SELECT required_amount, remaining_quantity
                FROM reward
                WHERE reward_id = ?
                  AND project_id = ?
                FOR UPDATE
                """;

            try (PreparedStatement ps =
                         conn.prepareStatement(rewardSql)) {

                ps.setInt(1, rewardId);
                ps.setInt(2, projectId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new BusinessException(
                                "해당 프로젝트의 리워드를 찾을 수 없습니다."
                        );
                    }

                    amount = rs.getBigDecimal("required_amount")
                            .multiply(BigDecimal.valueOf(quantity));

                    int value = rs.getInt("remaining_quantity");
                    remaining = rs.wasNull() ? null : value;
                }
            }

            // 4. 잔여 수량 확인
            // remaining_quantity가 NULL이면 수량 제한 없음
            if (remaining != null && remaining < quantity) {
                throw new BusinessException(
                        "리워드 수량이 부족합니다. (잔여 "
                                + remaining + "개, 신청 "
                                + quantity + "개)"
                );
            }

            // 5. 수량 제한 리워드의 재고 차감
            if (remaining != null) {
                String updateSql = """
                    UPDATE reward
                    SET remaining_quantity = remaining_quantity - ?
                    WHERE reward_id = ?
                      AND project_id = ?
                      AND remaining_quantity >= ?
                    """;

                try (PreparedStatement ps =
                             conn.prepareStatement(updateSql)) {

                    ps.setInt(1, quantity);
                    ps.setInt(2, rewardId);
                    ps.setInt(3, projectId);
                    ps.setInt(4, quantity);

                    if (ps.executeUpdate() != 1) {
                        throw new BusinessException(
                                "리워드 수량이 부족합니다."
                        );
                    }
                }
            }

            // 6. 기본 배송지와 함께 펀딩 기록 저장
            String fundingSql = """
                INSERT INTO funding
                (
                    supporter_id,
                    project_id,
                    reward_id,
                    address_id,
                    funding_amount,
                    quantity
                )
                VALUES (
                    ?, ?, ?,
                    (
                        SELECT address_id
                        FROM shipping_address
                        WHERE user_id = ?
                          AND is_default = TRUE
                        LIMIT 1
                    ),
                    ?, ?
                )
                """;

            try (PreparedStatement ps =
                         conn.prepareStatement(fundingSql)) {

                ps.setLong(1, supporterId);
                ps.setInt(2, projectId);
                ps.setInt(3, rewardId);
                ps.setLong(4, supporterId);
                ps.setBigDecimal(5, amount);
                ps.setInt(6, quantity);

                ps.executeUpdate();
            }

            // 7. 모든 작업 성공 시 커밋
            conn.commit();

            return amount;

        } catch (Exception e) {
            // 오류 발생 시 재고 차감 및 펀딩 기록 롤백
            conn.rollback();
            throw e;

        } finally {
            conn.setAutoCommit(true);
        }
    }

    public static void test10ConcurrencyControl() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {

            // 1. 수량 한정 리워드 목록
            String rewardSql = """
                    SELECT p.project_id, p.project_name,
                           r.reward_id, r.reward_name, r.required_amount,
                           r.remaining_quantity
                    FROM project p
                    JOIN reward r ON p.project_id = r.project_id
                    WHERE p.project_status = '진행중'
                      AND r.remaining_quantity IS NOT NULL
                    ORDER BY r.reward_id
                    """;

            System.out.println("\n========== 수량 한정 리워드 ==========");

            List<Integer> rewardIds = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(rewardSql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    rewardIds.add(rs.getInt("reward_id"));

                    System.out.printf("리워드 %d %s (프로젝트 %d %s) | 1개당 %,.0f원 | 잔여 %d개%n",
                            rs.getInt("reward_id"),
                            rs.getString("reward_name"),
                            rs.getInt("project_id"),
                            rs.getString("project_name"),
                            rs.getBigDecimal("required_amount"),
                            rs.getInt("remaining_quantity"));
                }
            }

            int rewardId = readInt("\n리워드 ID: ");

            // 목록에 없는 번호는 수량을 묻기 전에 바로 거부
            if (!rewardIds.contains(rewardId)) {
                throw new BusinessException("잘못된 리워드 번호입니다: " + rewardId
                        + " (위 목록의 리워드 ID를 입력하세요)");
            }

            int quantity = readInt("신청 수량: ");

            int projectId;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT project_id, remaining_quantity FROM reward WHERE reward_id = ?")) {
                ps.setInt(1, rewardId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getObject("remaining_quantity") == null) {
                        throw new BusinessException("수량 한정 리워드가 아닙니다.");
                    }

                    projectId = rs.getInt("project_id");
                }
            }

            // 2. 이 프로젝트에 아직 후원하지 않은 서포터 1명 자동 선택
            //    (1인 1프로젝트 1후원 규칙 때문에 같은 서포터로는 두 번 신청할 수 없음)
            long supporterId;

            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT u.id
                    FROM users u
                    WHERE u.role = 'SUPPORTER'
                      AND NOT EXISTS (
                          SELECT 1 FROM funding f
                          WHERE f.supporter_id = u.id
                            AND f.project_id = ?
                            AND f.funding_status = '후원완료')
                    ORDER BY u.id
                    LIMIT 1
                    """)) {
                ps.setInt(1, projectId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new BusinessException(
                                "이 프로젝트에 신청할 수 있는 서포터가 없습니다. initDB를 다시 실행하세요.");
                    }

                    supporterId = rs.getLong("id");
                }
            }

            // 3. 신청
            //    성공: 펀딩 기록 +1, 잔여 수량 감소 / 실패: 둘 다 변화 없음 (롤백)
            //    결과는 Workbench에서 reward, funding 테이블로 확인

            String remainingSql = "SELECT remaining_quantity FROM reward WHERE reward_id = ?";
            int remainingBefore = countRows(conn, remainingSql, rewardId);

            try {
                BigDecimal amount = applyReward(conn, supporterId,
                        projectId, rewardId, quantity);
                int remainingAfter = countRows(conn, remainingSql, rewardId);
                System.out.printf("%n결과: 성공 - 후원 금액 %,.0f원 (잔여 %d개 → %d개, 신청 %d개)%n",
                        amount, remainingBefore, remainingAfter, quantity);
            } catch (BusinessException e) {
                System.out.println("\n결과: 실패 - " + e.getMessage());
            }
        }
    }
}
