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

public class AdminService {
    public static void test04ApproveProject() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            long adminId = findUserId(conn, ADMIN_ID);

            // 1. 승인 대기 프로젝트: 상태가 '대기'이고 최근 이력이 신청/재신청
            String listSql = """
                    SELECT p.project_id, p.project_name, p.category,
                           p.target_amount, p.start_date, p.end_date,
                           u.name AS maker_name,
                           (SELECT COUNT(*) FROM reward r
                            WHERE r.project_id = p.project_id) AS reward_count
                    FROM project p
                    JOIN users u ON p.maker_id = u.id
                    JOIN approval_history a
                      ON a.history_id = (
                          SELECT MAX(a2.history_id)
                          FROM approval_history a2
                          WHERE a2.project_id = p.project_id
                      )
                    WHERE p.project_status = '대기'
                      AND a.action IN ('신청', '재신청')
                    ORDER BY p.project_id
                    """;

            List<Integer> projectIds = new ArrayList<>();

            System.out.println("\n========== 승인 대기 프로젝트 ==========");

            try (PreparedStatement ps = conn.prepareStatement(listSql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    projectIds.add(rs.getInt("project_id"));

                    System.out.printf("[%d] %s | %s | 메이커 %s | 목표 %,.0f원 | %s ~ %s | 리워드 %d개%n",
                            rs.getInt("project_id"),
                            rs.getString("project_name"),
                            rs.getString("category"),
                            rs.getString("maker_name"),
                            rs.getBigDecimal("target_amount"),
                            rs.getDate("start_date"),
                            rs.getDate("end_date"),
                            rs.getInt("reward_count"));
                }
            }

            if (projectIds.isEmpty()) {
                System.out.println("승인 대기 중인 프로젝트가 없습니다.");
                return;
            }

            int projectId = readInt("\n승인할 프로젝트 ID (취소는 0): ");

            if (projectId == 0) {
                System.out.println("프로젝트 승인을 취소했습니다.");
                return;
            }

            if (!projectIds.contains(projectId)) {
                throw new BusinessException("목록에 없는 프로젝트 ID입니다.");
            }

            conn.setAutoCommit(false);

            try {
                // 2. 상태가 아직 '대기'일 때만 변경
                // 두 관리자가 동시에 승인해도 한 명만 영향 행 1을 얻는다.
                String updateSql = """
                        UPDATE project
                        SET project_status = '진행중'
                        WHERE project_id = ?
                          AND project_status = '대기'
                        """;

                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, projectId);

                    if (ps.executeUpdate() == 0) {
                        throw new BusinessException("이미 처리된 프로젝트입니다.");
                    }
                }

                // 3. 승인 이력 저장
                insertApproval(conn, projectId, adminId, "승인");

                conn.commit();
                System.out.println("프로젝트 승인 완료. 프로젝트 ID: " + projectId);

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
