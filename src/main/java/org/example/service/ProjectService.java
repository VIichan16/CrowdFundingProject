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

public class ProjectService {

    // 문자열의 실제 출력 폭 계산 (한글 2칸, 영문/숫자 1칸)
    private static int getDisplayWidth(String str) {
        int width = 0;

        for (char ch : str.toCharArray()) {
            width += (ch >= 0xAC00 && ch <= 0xD7A3) ? 2 : 1;
        }

        return width;
    }


    private static String padRight(String str, int width) {
        int space = width - getDisplayWidth(str);
        return str + " ".repeat(Math.max(0, space));
    }
    public static void test06ViewProjects() throws Exception { //TODO 처음엔 프로젝트 목록만 뜨게 구현하고 프로젝트 ID를 선택하면 상세 정보가 뜨게.

        String sql = """
            SELECT p.project_id, p.project_name, p.category,
                   p.project_status, p.description, p.target_amount,
                   p.start_date, p.end_date,
                   u.name AS maker_name,
                   COALESCE(SUM(f.funding_amount), 0) AS total_funding,
                   ROUND(COALESCE(SUM(f.funding_amount), 0)
                         / p.target_amount * 100, 1) AS achievement_rate,
                   (SELECT COUNT(*) FROM project_like l
                    WHERE l.project_id = p.project_id) AS like_count,
                   (SELECT COUNT(*) FROM project_review rv
                    WHERE rv.project_id = p.project_id) AS review_count,
                   (SELECT ROUND(AVG(rv.rating), 1) FROM project_review rv
                    WHERE rv.project_id = p.project_id) AS avg_rating
            FROM project p
            JOIN users u ON p.maker_id = u.id
            LEFT JOIN funding f
                ON p.project_id = f.project_id
               AND f.funding_status = '후원완료'
            WHERE p.project_status IN ('대기', '진행중', '성공', '실패', '종료')
            GROUP BY p.project_id, p.project_name, p.category,
                     p.project_status, p.description, p.target_amount,
                     p.start_date, p.end_date, u.name
            ORDER BY p.project_id
            """;

        String reviewSql = """
            SELECT u.name, rv.rating, rv.content
            FROM project_review rv
            JOIN users u ON rv.supporter_id = u.id
            WHERE rv.project_id = ?
            ORDER BY rv.review_id
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             PreparedStatement reviewPs = conn.prepareStatement(reviewSql);
             ResultSet rs = ps.executeQuery()) {

            boolean found = false;

            // 프로젝트 목록 테이블 헤더
            System.out.println("\n============================== 프로젝트 목록 ==============================");
            System.out.println("----------------------------------------------------------------------------");
            System.out.printf("%-5s %-20s %-12s %-12s %-10s%n",
                    "ID", "프로젝트명", "분류", "메이커", "상태");
            System.out.println("----------------------------------------------------------------------------");

            // 프로젝트 목록 출력
            while (rs.next()) {
                found = true;

                String status = rs.getString("project_status");

                System.out.printf("%-5d %-20s %-12s %-12s %-10s%n",
                        rs.getInt("project_id"),
                        rs.getString("project_name"),
                        rs.getString("category"),
                        rs.getString("maker_name"),
                        "대기".equals(status) ? "승인 대기" : status);
            }

            if (!found) {
                System.out.println("조회할 프로젝트가 없습니다.");
                return;
            }

            System.out.println("============================================================================");

            // ResultSet을 다시 조회하기 위해 새 쿼리 실행
            try (ResultSet detailRs = ps.executeQuery()) {

                while (detailRs.next()) {

                    int projectId = detailRs.getInt("project_id");
                    int reviewCount = detailRs.getInt("review_count");

                    System.out.println("\n┌──────────────────────── 프로젝트 상세 정보 ────────────────────────┐");

                    System.out.printf("│ ID       : %-58d │%n", projectId);
                    System.out.printf("│ 프로젝트 : %-58s │%n",
                            detailRs.getString("project_name"));
                    System.out.printf("│ 상태     : %-58s │%n",
                            detailRs.getString("project_status"));
                    System.out.printf("│ 분류     : %-58s │%n",
                            detailRs.getString("category"));
                    System.out.printf("│ 메이커   : %-58s │%n",
                            detailRs.getString("maker_name"));

                    System.out.println("├────────────────────────────────────────────────────────────────────┤");

                    System.out.println("│ 설명     : " + detailRs.getString("description"));
                    System.out.printf("│ 모금액   : %,.0f원 / 목표 %,.0f원%n",
                            detailRs.getBigDecimal("total_funding"),
                            detailRs.getBigDecimal("target_amount"));
                    System.out.printf("│ 달성률   : %.1f%%%n",
                            detailRs.getBigDecimal("achievement_rate"));

                    System.out.printf("│ 기간     : %s ~ %s%n",
                            detailRs.getDate("start_date"),
                            detailRs.getDate("end_date"));

                    System.out.printf("│ 좋아요   : %d개%n",
                            detailRs.getInt("like_count"));

                    System.out.printf("│ 리뷰     : %d개",
                            reviewCount);

                    if (reviewCount > 0) {
                        System.out.printf(" (평균 ★%s)",
                                detailRs.getBigDecimal("avg_rating"));
                    }

                    System.out.println();
                    System.out.println("├──────────────────────── 리뷰 목록 ────────────────────────────────┤");

                    reviewPs.setInt(1, projectId);

                    try (ResultSet rr = reviewPs.executeQuery()) {

                        if (!rr.isBeforeFirst()) {
                            System.out.println("│ 등록된 리뷰가 없습니다.");
                        }

                        while (rr.next()) {
                            System.out.printf("│ %s ★%d : %s%n",
                                    rr.getString("name"),
                                    rr.getInt("rating"),
                                    rr.getString("content"));
                        }
                    }

                    System.out.println("└────────────────────────────────────────────────────────────────────┘");
                }
            }
        }
    }

    public static void test07FilterProjects() throws Exception {
        String category = selectCategory(true);

        System.out.println("진행 상태를 선택하세요:");
        System.out.println("1. 진행");
        System.out.println("2. 마감");
        String stateInput = readLine("선택 (전체는 Enter): ");

        if (!stateInput.isEmpty()
                && !"1".equals(stateInput) && !"2".equals(stateInput)) {
            throw new BusinessException("올바른 진행 상태 번호를 선택하세요.");
        }

        String keyword = readLine("프로젝트명 검색어 (전체는 Enter): ");

        StringBuilder sql = new StringBuilder("""
                SELECT p.project_id, p.project_name, p.category,
                       p.target_amount, p.project_status, p.end_date
                FROM project p
                WHERE p.project_status IN """ + PUBLIC_STATUS);

        List<Object> params = new ArrayList<>();

        if (!keyword.isEmpty()) {
            sql.append(" AND p.project_name LIKE ?");
            params.add("%" + keyword + "%");
        }

        if (category != null) {
            sql.append(" AND p.category = ?");
            params.add(category);
        }

        if ("1".equals(stateInput)) {
            sql.append(" AND p.project_status = '진행중'");
        } else if ("2".equals(stateInput)) {
            sql.append(" AND p.project_status IN ").append(CLOSED_STATUS);
        }

        sql.append(" ORDER BY p.project_id");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;

                System.out.println("\n조건: 카테고리=" + (category == null ? "전체" : category)
                        + ", 상태=" + ("1".equals(stateInput) ? "진행"
                        : "2".equals(stateInput) ? "마감" : "전체")
                        + ", 검색어=" + (keyword.isEmpty() ? "전체" : keyword));

                while (rs.next()) {
                    found = true;

                    System.out.printf("%d | %s | %s | 목표 %,.0f원 | %s | 마감일 %s%n",
                            rs.getInt("project_id"),
                            rs.getString("project_name"),
                            rs.getString("category"),
                            rs.getBigDecimal("target_amount"),
                            rs.getString("project_status"),
                            rs.getDate("end_date"));
                }

                if (!found) {
                    System.out.println("검색 결과가 없습니다.");
                }
            }
        }
    }
}
