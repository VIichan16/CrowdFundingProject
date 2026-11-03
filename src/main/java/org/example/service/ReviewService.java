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

public class ReviewService {
    public static void test09RegisterReview() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            long supporterId = findUserId(conn, SUPPORTER_ID);

            // 1. 리뷰 작성 가능한 프로젝트와 내가 받은 리워드
            String listSql = """
                    SELECT p.project_id, p.project_name, r.reward_name
                    FROM funding f
                    JOIN project p ON f.project_id = p.project_id
                    JOIN reward r ON f.reward_id = r.reward_id
                    WHERE f.supporter_id = ?
                      AND f.funding_status = '후원완료'
                      AND p.project_status IN ('성공', '종료')
                    ORDER BY p.project_id
                    """;

            List<Integer> projectIds = new ArrayList<>();
            List<String> rewardNames = new ArrayList<>();

            System.out.println("\n===== 리뷰 작성 가능한 프로젝트 =====");

            try (PreparedStatement ps = conn.prepareStatement(listSql)) {
                ps.setLong(1, supporterId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        projectIds.add(rs.getInt("project_id"));
                        rewardNames.add(rs.getString("reward_name"));

                        System.out.println("프로젝트 ID: " + rs.getInt("project_id")
                                + " | " + rs.getString("project_name")
                                + " | 리워드: " + rs.getString("reward_name"));
                    }
                }
            }

            if (projectIds.isEmpty()) {
                System.out.println("리뷰를 작성할 수 있는 프로젝트가 없습니다.");
                return;
            }

            int projectId = readInt("\n프로젝트 ID: ");
            int index = projectIds.indexOf(projectId);

            if (index < 0) {
                throw new BusinessException("목록에 없는 프로젝트 ID입니다.");
            }

            int rating = readInt("별점 (1~5): ");
            if (rating < 1 || rating > 5) {
                throw new BusinessException("별점은 1~5 사이로 입력하세요.");
            }

            String content = readLine("리뷰 내용: ");
            if (content.isBlank()) {
                throw new BusinessException("리뷰 내용을 입력하세요.");
            }

            // 2. 이미 쓴 리뷰가 있으면 수정, 없으면 등록
            boolean hasReview = exists(conn,
                    "SELECT 1 FROM project_review WHERE project_id = ? AND supporter_id = ?",
                    projectId, supporterId);

            String sql = hasReview
                    ? """
                      UPDATE project_review
                      SET rating = ?, content = ?
                      WHERE project_id = ? AND supporter_id = ?
                      """
                    : """
                      INSERT INTO project_review
                      (rating, content, project_id, supporter_id)
                      VALUES (?, ?, ?, ?)
                      """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, rating);
                ps.setString(2, content);
                ps.setInt(3, projectId);
                ps.setLong(4, supporterId);
                ps.executeUpdate();
            }

            System.out.println((hasReview ? "리뷰 수정 완료" : "리뷰 등록 완료")
                    + " | 리워드: " + rewardNames.get(index)
                    + " | 별점: " + rating + " | 리뷰: " + content);
        }
    }
}
