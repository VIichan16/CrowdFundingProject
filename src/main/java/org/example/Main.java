
package org.example;
import org.example.persistence.DBConnection;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.Scanner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;


public class Main {
    private static final Scanner sc = new Scanner(System.in);

    private static final String MAKER_ID = "test_maker";
    private static final String SUPPORTER_ID = "test_supporter";
    private static final String ADMIN_ID = "test_admin";

    public static void main(String[] args) {
        try {
            initDB();

            // 검사할 항목 하나만 주석 해제하여 실행
            //test01ViewFundingStatus();      // MAKER - 프로젝트별 펀딩 현황 보기
            //test02RegisterProject();        // MAKER - 프로젝트 기본 정보 등록
            //test03RegisterReward();         // MAKER - 리워드 등록 및 프로젝트 신청
            //test04ApproveProject();         // ADMIN - 프로젝트 등록 승인
            //test05UpdateProfile();          // SUPPORTER - 개인정보 수정
            //test06ViewProjects();           // SUPPORTER - 프로젝트 보기
            //test07FilterProjects();         // SUPPORTER - 프로젝트 필터링
            //test08ApplyReward();             // SUPPORTER - 리워드 신청
            //test09RegisterReview();          // SUPPORTER - 리뷰 및 별점 등록
            //test10ConcurrencyControl();     // SUPPORTER - 제공 가능 수량 동시성 제어

            System.out.println("테스트가 종료되었습니다.");

        } catch (Exception e) {
            System.out.println("테스트 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================================================
    // 공통 DB 도우미
    // ==================================================

    private static long findUserId(Connection conn, String loginId)
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

    private static int findProjectId(Connection conn, String projectName)
            throws SQLException {
        String sql = """
                SELECT project_id
                FROM project
                WHERE project_name = ?
                ORDER BY project_id DESC
                LIMIT 1
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectName);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "프로젝트를 찾을 수 없습니다: " + projectName);
                }

                return rs.getInt("project_id");
            }
        }
    }

    private static int findRewardId(Connection conn, int projectId)
            throws SQLException {
        String sql = """
                SELECT reward_id
                FROM reward
                WHERE project_id = ?
                ORDER BY reward_id DESC
                LIMIT 1
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("리워드가 없습니다.");
                }

                return rs.getInt("reward_id");
            }
        }
    }

    private static boolean exists(Connection conn, String sql, Object... values)
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

    // ==================================================
    // 사전 데이터 생성
    // ==================================================


    private static void initDB() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 테스트 사용자 생성
                insertTestUser(conn, MAKER_ID, "테스트 메이커",
                        "010-1111-1111", "MAKER");

                insertTestUser(conn, SUPPORTER_ID, "테스트 서포터",
                        "010-2222-2222", "SUPPORTER");

                insertTestUser(conn, ADMIN_ID, "테스트 관리자",
                        "010-3333-3333", "ADMIN");

                long makerId = findUserId(conn, MAKER_ID);

                String projectName = "테스트용 프로젝트";

                // 테스트 프로젝트가 없으면 생성
                if (!exists(conn,
                        "SELECT 1 FROM project WHERE project_name = ?",
                        projectName)) {

                    String sql = """
                            INSERT INTO project
                            (maker_id, project_name, category, description,
                             target_amount, start_date, end_date, project_status)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """;

                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setLong(1, makerId);
                        ps.setString(2, projectName);
                        ps.setString(3, "테스트");
                        ps.setString(4, "테스트용 프로젝트입니다.");
                        ps.setBigDecimal(5, new BigDecimal("100000"));
                        ps.setDate(6, Date.valueOf(LocalDate.now()));
                        ps.setDate(7, Date.valueOf(LocalDate.now().plusDays(30)));
                        ps.setString(8, "진행중");

                        ps.executeUpdate();
                    }
                }

                int projectId = findProjectId(conn, projectName);

                // 테스트 리워드가 없으면 생성
                if (!exists(conn,
                        "SELECT 1 FROM reward WHERE project_id = ?",
                        projectId)) {

                    String sql = """
                            INSERT INTO reward
                            (project_id, reward_name, description,
                             required_amount, total_quantity,
                             remaining_quantity)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """;

                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, projectId);
                        ps.setString(2, "테스트 리워드");
                        ps.setString(3, "테스트용 리워드입니다.");
                        ps.setBigDecimal(4, new BigDecimal("10000"));
                        ps.setInt(5, 100);
                        ps.setInt(6, 100);

                        ps.executeUpdate();
                    }
                }

                conn.commit();
                System.out.println("테스트용 사전 데이터 준비 완료.");

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }


    private static void insertTestUser(
            Connection conn, String loginId, String name,
            String phone, String role) throws SQLException {

        if (exists(conn,
                "SELECT 1 FROM users WHERE login_id = ?", loginId)) {
            return;
        }

        String sql = """
                INSERT INTO users
                (login_id, password, name, phone_number, role, status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loginId);

            // 테스트 전용 값이며 실제 로그인용 비밀번호 해시가 아닙니다.
            ps.setString(2, "TEST_ONLY");

            ps.setString(3, name);
            ps.setString(4, phone);
            ps.setString(5, role);
            ps.executeUpdate();
        }
    }

    // ==================================================
    // test01: 프로젝트별 펀딩 현황
    // ==================================================


    private static void test01ViewFundingStatus() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            long makerId = findUserId(conn, MAKER_ID);

            String sql = """
                    SELECT p.project_id, p.project_name,
                           p.target_amount, p.project_status,
                           COALESCE(SUM(
                               CASE WHEN f.funding_status = '후원완료'
                                    THEN f.funding_amount ELSE 0 END
                           ), 0) AS total_funding
                    FROM project p
                    LEFT JOIN funding f
                        ON p.project_id = f.project_id
                    WHERE p.maker_id = ?
                    GROUP BY p.project_id, p.project_name,
                             p.target_amount, p.project_status
                    ORDER BY p.project_id
                    """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, makerId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("\n프로젝트 ID: "
                                + rs.getInt("project_id"));

                        System.out.println("프로젝트명: "
                                + rs.getString("project_name"));

                        System.out.println("목표 금액: "
                                + rs.getBigDecimal("target_amount"));

                        System.out.println("현재 후원 금액: "
                                + rs.getBigDecimal("total_funding"));

                        System.out.println("프로젝트 상태: "
                                + rs.getString("project_status"));
                    }
                }
            }
        }
    }

    // ==================================================
    // test02: 프로젝트 등록
    // ==================================================


    private static void test02RegisterProject() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                long makerId = findUserId(conn, MAKER_ID);

                System.out.print("프로젝트명: ");
                String name = sc.nextLine().trim();

                // ==================== 카테고리 선택 기능 구현 ====================
                System.out.println("카테고리를 선택하세요:");
                System.out.println("1. 식품");
                System.out.println("2. 가전");
                System.out.print("선택 (1~2): ");

                String categoryInput = sc.nextLine().trim();
                String category = "";

                if ("1".equals(categoryInput)) {
                    category = "식품";
                } else if ("2".equals(categoryInput)) {
                    category = "가전";
                } else {
                    System.out.println("올바른 카테고리 번호를 선택하세요.");
                    conn.rollback();
                    return;
                }
                // ================================================================

                System.out.print("설명: ");
                String description = sc.nextLine().trim();

                System.out.print("목표 금액: ");
                BigDecimal target;
                try {
                    target = new BigDecimal(sc.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("목표 금액은 숫자로 입력해야 합니다.");
                    conn.rollback();
                    return;
                }

                if (name.isBlank() || target.signum() <= 0) {
                    System.out.println("입력값을 확인하세요.");
                    conn.rollback();
                    return;
                }

                String sql = """
                    INSERT INTO project
                    (maker_id, project_name, category, description,
                     target_amount, start_date, end_date, project_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, '대기')
                    """;

                int projectId;

                try (PreparedStatement ps = conn.prepareStatement(
                        sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, makerId);
                    ps.setString(2, name);
                    ps.setString(3, category);
                    ps.setString(4, description);
                    ps.setBigDecimal(5, target);
                    ps.setDate(6, Date.valueOf(LocalDate.now()));
                    ps.setDate(7, Date.valueOf(LocalDate.now().plusDays(30)));

                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new SQLException("프로젝트 ID를 가져오지 못했습니다.");
                        }
                        projectId = rs.getInt(1);
                    }
                }

                // 승인 신청 이력 저장
                String historySql = """
                    INSERT INTO approval_history
                    (project_id, actor_id, action, reason)
                    VALUES (?, ?, '신청', NULL)
                    """;

                try (PreparedStatement ps = conn.prepareStatement(historySql)) {
                    ps.setInt(1, projectId);
                    ps.setLong(2, makerId);
                    ps.executeUpdate();
                }

                conn.commit();
                System.out.println("프로젝트 등록 신청 완료. 관리자 승인을 기다립니다.");

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // ==================================================
    // test03: 리워드 등록 및 승인 신청 //TODO 메뉴출력(프로젝트 목록리스트만)
    // ==================================================

    private static void test03RegisterReward() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            long makerId = findUserId(conn, MAKER_ID);

            // 1. 현재 메이커의 프로젝트 목록 출력
            System.out.println("\n===== 내 프로젝트 목록 =====");

            String projectSql = """
                SELECT project_id, project_name, category, project_status
                FROM project
                WHERE maker_id = ?
                  AND project_status IN ('대기', '진행중')
                ORDER BY project_id
            """;
            boolean hasProject = false;

            try (PreparedStatement ps = conn.prepareStatement(projectSql)) {
                ps.setLong(1, makerId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        hasProject = true;

                        System.out.println(
                                "프로젝트 ID: " + rs.getInt("project_id")
                                        + " | 프로젝트명: " + rs.getString("project_name")
                                        + " | 카테고리: " + rs.getString("category")
                                        + " | 상태: " + rs.getString("project_status")
                        );
                    }
                }
            }

            if (!hasProject) {
                System.out.println("등록된 프로젝트가 없습니다.");
                return;
            }

            // 2. 목록을 보고 프로젝트 ID 입력
            System.out.print("\n리워드를 등록할 프로젝트 ID: ");

            int projectId;
            try {
                projectId = Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("올바른 프로젝트 ID를 입력하세요.");
                return;
            }

            // 선택한 프로젝트가 현재 메이커의 프로젝트인지 확인
            if (!exists(conn,
                    "SELECT 1 FROM project WHERE project_id = ? AND maker_id = ?",
                    projectId, makerId)) {
                System.out.println("본인의 프로젝트가 아니거나 존재하지 않습니다.");
                return;
            }

            // 3. 리워드 정보 입력
            System.out.print("리워드명: ");
            String name = sc.nextLine().trim();

            System.out.print("리워드 설명: ");
            String description = sc.nextLine().trim();

            System.out.print("필요 후원 금액: ");
            BigDecimal amount;

            try {
                amount = new BigDecimal(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("올바른 후원 금액을 입력하세요.");
                return;
            }

            System.out.print("총 수량 (무제한은 0): ");

            int quantity;
            try {
                quantity = Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("올바른 수량을 입력하세요.");
                return;
            }

            if (name.isBlank() || description.isBlank()
                    || amount.signum() <= 0 || quantity < 0) {
                System.out.println("입력값을 확인하세요.");
                return;
            }

            conn.setAutoCommit(false);

            try {
                // 4. 리워드 등록
                String sql = """
                        INSERT INTO reward
                        (project_id, reward_name, description,
                         required_amount, total_quantity, remaining_quantity)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """;

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, projectId);
                    ps.setString(2, name);
                    ps.setString(3, description);
                    ps.setBigDecimal(4, amount);

                    if (quantity == 0) {
                        // NULL은 수량 제한 없음
                        ps.setNull(5, Types.INTEGER);
                        ps.setNull(6, Types.INTEGER);
                    } else {
                        ps.setInt(5, quantity);
                        ps.setInt(6, quantity);
                    }

                    ps.executeUpdate();
                }

                // 5. 프로젝트 상태를 대기로 변경
                String updateSql = """
                        UPDATE project
                        SET project_status = '대기'
                        WHERE project_id = ? AND maker_id = ?
                        """;

                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, projectId);
                    ps.setLong(2, makerId);
                    ps.executeUpdate();
                }

                // 6. 승인 신청 이력 등록
                String historySql = """
                        INSERT INTO approval_history
                        (project_id, actor_id, action, reason)
                        VALUES (?, ?, '신청', NULL)
                        """;

                try (PreparedStatement ps = conn.prepareStatement(historySql)) {
                    ps.setInt(1, projectId);
                    ps.setLong(2, makerId);
                    ps.executeUpdate();
                }

                conn.commit();
                System.out.println("리워드 등록 및 승인 신청 완료.");

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }


// ==================================================
// test04: 프로젝트 승인
// ==================================================


    private static void test04ApproveProject() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            long adminId = findUserId(conn, ADMIN_ID);

            // 1. 승인 대기 프로젝트 조회
            String listSql = """
            SELECT p.project_id,
                   p.project_name,
                   p.category,
                   p.description,
                   p.target_amount,
                   p.start_date,
                   p.end_date
            FROM project p
            JOIN approval_history a
              ON a.history_id = (
                  SELECT MAX(a2.history_id)
                  FROM approval_history a2
                  WHERE a2.project_id = p.project_id
              )
            WHERE a.action IN ('신청', '재신청')
            ORDER BY p.project_id
            """;

            List<Integer> projectIds = new ArrayList<>();

            System.out.println();
            System.out.println("==============================================================");
            System.out.println("                     프로젝트 승인 관리");
            System.out.println("==============================================================");

            try (PreparedStatement ps = conn.prepareStatement(listSql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    int id = rs.getInt("project_id");
                    String name = rs.getString("project_name");
                    String category = rs.getString("category");
                    String description = rs.getString("description");
                    java.math.BigDecimal targetAmount =
                            rs.getBigDecimal("target_amount");
                    String startDate = rs.getString("start_date");
                    String endDate = rs.getString("end_date");

                    projectIds.add(id);

                    System.out.println();
                    System.out.println("--------------------------------------------------------------");
                    System.out.printf("  [%d] %s%n", id, name);
                    System.out.println("--------------------------------------------------------------");
                    System.out.printf("  카테고리   : %s%n", category);
                    System.out.printf("  목표 금액  : %, .0f원%n",
                            targetAmount.doubleValue());
                    System.out.printf("  시작일     : %s%n", startDate);
                    System.out.printf("  종료일     : %s%n", endDate);
                    System.out.println("  프로젝트 설명:");

                    if (description == null || description.isBlank()) {
                        System.out.println("    등록된 설명이 없습니다.");
                    } else {
                        // 설명이 길어도 읽기 편하도록 줄 단위로 출력
                        for (String line : description.split("\\R")) {
                            System.out.println("    " + line);
                        }
                    }
                }
            }

            if (projectIds.isEmpty()) {
                System.out.println();
                System.out.println("  승인 대기 중인 프로젝트가 없습니다.");
                System.out.println("==============================================================");
                return;
            }

            // 2. 승인할 프로젝트 선택
            System.out.println();
            System.out.println("==============================================================");
            System.out.println("  0. 취소");
            System.out.println("==============================================================");
            System.out.print("  승인할 프로젝트 ID 입력: ");

            String input = sc.nextLine().trim();

            if (input.equals("0")) {
                System.out.println("  프로젝트 승인을 취소했습니다.");
                return;
            }

            int projectId;

            try {
                projectId = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("  올바른 프로젝트 ID를 입력하세요.");
                return;
            }

            if (!projectIds.contains(projectId)) {
                System.out.println("  목록에 없는 프로젝트 ID입니다.");
                return;
            }

            // 3. 트랜잭션 시작
            conn.setAutoCommit(false);

            try {
                String checkSql = """
                SELECT a.action
                FROM approval_history a
                WHERE a.project_id = ?
                ORDER BY a.history_id DESC
                LIMIT 1
                FOR UPDATE
                """;

                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setInt(1, projectId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()
                                || !(rs.getString("action").equals("신청")
                                || rs.getString("action").equals("재신청"))) {
                            throw new SQLException(
                                    "승인 대기 상태의 프로젝트가 아닙니다.");
                        }
                    }
                }

                // 4. 프로젝트 상태 변경
                String updateSql = """
                UPDATE project
                SET project_status = '진행중'
                WHERE project_id = ?
                """;

                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, projectId);

                    if (ps.executeUpdate() == 0) {
                        throw new SQLException("프로젝트를 찾을 수 없습니다.");
                    }
                }

                // 5. 승인 이력 저장
                String historySql = """
                INSERT INTO approval_history
                    (project_id, actor_id, action, reason)
                VALUES (?, ?, '승인', NULL)
                """;

                try (PreparedStatement ps =
                             conn.prepareStatement(historySql)) {
                    ps.setInt(1, projectId);
                    ps.setLong(2, adminId);
                    ps.executeUpdate();
                }

                conn.commit();

                System.out.println();
                System.out.println("==============================================================");
                System.out.println("                  프로젝트 승인 완료!");
                System.out.println("  프로젝트 ID: " + projectId);
                System.out.println("==============================================================");

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }


// ==================================================
// test05: 서포터 개인정보 수정
// ==================================================

    private static void test05UpdateProfile() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            long supporterId = findUserId(conn, SUPPORTER_ID);

            System.out.print("변경할 이름: ");
            String name = sc.nextLine().trim();

            System.out.print("변경할 전화번호: ");
            String phone = sc.nextLine().trim();

            System.out.print("자기소개: ");
            String bio = sc.nextLine().trim();

            if (name.isBlank()
                    || !phone.matches("[0-9+() -]{8,20}")) {
                System.out.println("이름 또는 전화번호를 확인하세요.");
                return;
            }

            if (exists(conn,
                    "SELECT 1 FROM users WHERE phone_number = ? AND id <> ?",
                    phone, supporterId)) {
                System.out.println("이미 사용 중인 전화번호입니다.");
                return;
            }

            String sql = """
                    UPDATE users
                    SET name = ?, phone_number = ?, bio = ?
                    WHERE id = ?
                    """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, phone);
                ps.setString(3, bio);
                ps.setLong(4, supporterId);
                ps.executeUpdate();
            }

            System.out.println("개인정보 수정 완료.");
        }
    }


// ==================================================
// test06: 승인된 프로젝트 조회 //TODO 승인, 대기 , 종료 프로젝트 전부 보이게 리뉴얼
// ==================================================

    private static void test06ViewProjects() throws Exception {
        String sql = """
                SELECT p.project_id, p.project_name, p.category,
                       p.project_status, p.description, p.target_amount,
                       p.start_date, p.end_date,
                       u.name AS maker_name
                FROM project p
                JOIN users u ON p.maker_id = u.id
                WHERE p.project_status IN ('대기', '진행중', '성공', '실패')
                ORDER BY p.project_id DESC
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            boolean found = false;

            while (rs.next()) {
                found = true;

                System.out.println("\nID: " + rs.getInt("project_id"));
                System.out.println("프로젝트명: " + rs.getString("project_name"));
                System.out.println("상태: " + rs.getString("project_status"));
                System.out.println("분류: " + rs.getString("category"));
                System.out.println("메이커: " + rs.getString("maker_name"));
                System.out.println("설명: " + rs.getString("description"));
                System.out.println("목표 금액: " + rs.getBigDecimal("target_amount"));
                System.out.println("기간: " + rs.getDate("start_date")
                        + " ~ " + rs.getDate("end_date"));
            }

            if (!found) {
                System.out.println("조회할 프로젝트가 없습니다.");
            }
        }
    }


// ==================================================
// test07: 프로젝트 필터링 //TODO 카테고리 2개 제한
// ==================================================

    private static void test07FilterProjects() throws Exception {
        System.out.print("프로젝트명 검색어 (전체는 Enter): ");
        String name = sc.nextLine().trim();

        System.out.print("카테고리 검색어 (전체는 Enter): ");
        String category = sc.nextLine().trim();

        String sql = """
                SELECT p.project_id, p.project_name, p.category,
                       p.target_amount, p.project_status
                FROM project p
                JOIN approval_history a
                  ON a.history_id = (
                      SELECT MAX(a2.history_id)
                      FROM approval_history a2
                      WHERE a2.project_id = p.project_id
                  )
                WHERE a.action = '승인'
                  AND p.project_status = '진행중'
                  AND (? = '' OR p.project_name LIKE ?)
                  AND (? = '' OR p.category LIKE ?)
                ORDER BY p.project_id DESC
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, "%" + name + "%");
            ps.setString(3, category);
            ps.setString(4, "%" + category + "%");

            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;

                while (rs.next()) {
                    found = true;

                    System.out.println(
                            rs.getInt("project_id") + " | "
                                    + rs.getString("project_name") + " | "
                                    + rs.getString("category") + " | "
                                    + rs.getBigDecimal("target_amount") + " | "
                                    + rs.getString("project_status"));
                }

                if (!found) {
                    System.out.println("검색 결과가 없습니다.");
                }
            }
        }
    }


// ==================================================
// test08: 리워드 신청 및 후원
// ==================================================

    private static void test08ApplyReward() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            long supporterId = findUserId(conn, SUPPORTER_ID);

            System.out.print("프로젝트 ID: ");
            int projectId = Integer.parseInt(sc.nextLine().trim());

            System.out.print("리워드 ID: ");
            int rewardId = Integer.parseInt(sc.nextLine().trim());

            System.out.print("수량: ");
            int quantity = Integer.parseInt(sc.nextLine().trim());

            if (quantity <= 0) {
                System.out.println("수량은 1 이상이어야 합니다.");
                return;
            }

            conn.setAutoCommit(false);

            try {
                // 진행 중인 승인 프로젝트인지 확인
                String projectSql = """
                        SELECT project_id
                        FROM project
                        WHERE project_id = ?
                          AND project_status = '진행중'
                        """;

                if (!exists(conn, projectSql, projectId)) {
                    throw new SQLException(
                            "후원 가능한 진행 중 프로젝트가 아닙니다.");
                }

                BigDecimal amount;

                String rewardSql = """
                        SELECT required_amount, remaining_quantity
                        FROM reward
                        WHERE reward_id = ? AND project_id = ?
                        FOR UPDATE
                        """;

                try (PreparedStatement ps =
                             conn.prepareStatement(rewardSql)) {
                    ps.setInt(1, rewardId);
                    ps.setInt(2, projectId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                    "해당 프로젝트의 리워드를 찾을 수 없습니다.");
                        }

                        amount = rs.getBigDecimal("required_amount")
                                .multiply(BigDecimal.valueOf(quantity));

                        Object remaining =
                                rs.getObject("remaining_quantity");

                        if (remaining != null
                                && rs.getInt("remaining_quantity") < quantity) {
                            throw new SQLException("리워드 수량이 부족합니다.");
                        }
                    }
                }

                // 수량 제한이 있는 리워드만 차감
                try (PreparedStatement ps = conn.prepareStatement("""
                        UPDATE reward
                        SET remaining_quantity = remaining_quantity - ?
                        WHERE reward_id = ?
                          AND project_id = ?
                          AND remaining_quantity IS NOT NULL
                          AND remaining_quantity >= ?
                        """)) {
                    ps.setInt(1, quantity);
                    ps.setInt(2, rewardId);
                    ps.setInt(3, projectId);
                    ps.setInt(4, quantity);
                    ps.executeUpdate();
                }

                String fundingSql = """
                        INSERT INTO funding
                        (supporter_id, project_id, reward_id,
                         funding_amount, quantity)
                        VALUES (?, ?, ?, ?, ?)
                        """;

                try (PreparedStatement ps =
                             conn.prepareStatement(fundingSql)) {
                    ps.setLong(1, supporterId);
                    ps.setInt(2, projectId);
                    ps.setInt(3, rewardId);
                    ps.setBigDecimal(4, amount);
                    ps.setInt(5, quantity);
                    ps.executeUpdate();
                }

                conn.commit();

                System.out.println("리워드 신청 및 후원 완료.");
                System.out.println("후원 금액: " + amount);

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }


// ==================================================
// test09: 리뷰 및 별점 등록
// ==================================================

    private static void test09RegisterReview() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            long supporterId = findUserId(conn, SUPPORTER_ID);

            System.out.print("프로젝트 ID: ");
            int projectId = Integer.parseInt(sc.nextLine().trim());

            System.out.print("별점 (1~5): ");
            int rating = Integer.parseInt(sc.nextLine().trim());

            System.out.print("리뷰 내용: ");
            String content = sc.nextLine().trim();

            if (rating < 1 || rating > 5 || content.isBlank()) {
                System.out.println("별점 또는 리뷰 내용을 확인하세요.");
                return;
            }

            // 성공적으로 종료된 프로젝트인지 확인
            if (!exists(conn, """
                    SELECT 1 FROM project
                    WHERE project_id = ?
                      AND project_status IN ('성공', '종료')
                    """, projectId)) {
                System.out.println("종료된 프로젝트만 리뷰를 작성할 수 있습니다.");
                return;
            }

            if (!exists(conn, """
                    SELECT 1 FROM funding
                    WHERE project_id = ?
                      AND supporter_id = ?
                      AND funding_status = '후원완료'
                    """, projectId, supporterId)) {
                System.out.println("후원 완료한 프로젝트만 리뷰를 작성할 수 있습니다.");
                return;
            }

            String sql = """
                    INSERT INTO project_review
                    (project_id, supporter_id, rating, content)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        rating = VALUES(rating),
                        content = VALUES(content),
                        updated_at = CURRENT_TIMESTAMP
                    """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, projectId);
                ps.setLong(2, supporterId);
                ps.setInt(3, rating);
                ps.setString(4, content);
                ps.executeUpdate();
            }

            System.out.println("리뷰 저장 완료.");
        }
    }


    // ==================================================
    // test10: 리워드 수량 동시성 제어
    // ==================================================
    private static void test10ConcurrencyControl() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            Scanner sc = new Scanner(System.in);

            int projectId;
            int rewardId;

            // 1. 진행 중인 프로젝트 목록 출력
            String projectSql = """
                    SELECT project_id, project_name
                    FROM project
                    WHERE project_status = '진행중'
                    ORDER BY project_id
                    """;

            List<Integer> projectIds = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(projectSql);
                 ResultSet rs = ps.executeQuery()) {

                System.out.println("\n========== 진행 중인 프로젝트 ==========");

                while (rs.next()) {
                    int id = rs.getInt("project_id");
                    String name = rs.getString("project_name");

                    projectIds.add(id);

                    System.out.println(
                            projectIds.size() + ". " + name
                                    + " (프로젝트 ID: " + id + ")"
                    );
                }
            }

            if (projectIds.isEmpty()) {
                System.out.println("진행 중인 프로젝트가 없습니다.");
                return;
            }

            // 2. 프로젝트 선택
            System.out.print("\n프로젝트 선택: ");

            int projectChoice = sc.nextInt();

            if (projectChoice < 1 || projectChoice > projectIds.size()) {
                System.out.println("잘못된 프로젝트 번호입니다.");
                return;
            }

            projectId = projectIds.get(projectChoice - 1);

            // 3. 선택한 프로젝트의 리워드 목록 출력
            // reward_name을 추가하여 리워드 이름도 조회
            String rewardSql = """
                    SELECT reward_id, reward_name, remaining_quantity
                    FROM reward
                    WHERE project_id = ?
                    ORDER BY reward_id
                    """;

            List<Integer> rewardIds = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(rewardSql)) {
                ps.setInt(1, projectId);

                try (ResultSet rs = ps.executeQuery()) {
                    System.out.println("\n========== 리워드 목록 ==========");

                    while (rs.next()) {
                        int id = rs.getInt("reward_id");
                        String name = rs.getString("reward_name");
                        Object quantity = rs.getObject("remaining_quantity");

                        rewardIds.add(id);

                        String quantityText;

                        if (quantity == null) {
                            quantityText = "수량 제한 없음";
                        } else {
                            quantityText = "남은 수량: " + quantity + "개";
                        }

                        System.out.println(
                                rewardIds.size() + ". " + name
                                        + " (리워드 ID: " + id + ")"
                                        + " | " + quantityText
                        );
                    }
                }
            }

            if (rewardIds.isEmpty()) {
                System.out.println("해당 프로젝트에 등록된 리워드가 없습니다.");
                return;
            }

            // 4. 리워드 선택
            System.out.print("\n리워드 선택: ");

            int rewardChoice = sc.nextInt();

            if (rewardChoice < 1 || rewardChoice > rewardIds.size()) {
                System.out.println("잘못된 리워드 번호입니다.");
                return;
            }

            rewardId = rewardIds.get(rewardChoice - 1);

            System.out.println("\n선택한 프로젝트 ID: " + projectId);
            System.out.println("선택한 리워드 ID: " + rewardId);

            // 5. 트랜잭션 시작
            conn.setAutoCommit(false);

            try {
                int remaining;

                // 리워드 행 잠금
                String selectSql = """
                        SELECT remaining_quantity
                        FROM reward
                        WHERE reward_id = ?
                          AND project_id = ?
                        FOR UPDATE
                        """;

                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setInt(1, rewardId);
                    ps.setInt(2, projectId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("리워드를 찾을 수 없습니다.");
                        }

                        Object value = rs.getObject("remaining_quantity");

                        if (value == null) {
                            System.out.println("수량 제한이 없는 리워드입니다.");
                            conn.rollback();
                            return;
                        }

                        remaining = rs.getInt("remaining_quantity");
                    }
                }

                // 6. 남은 수량 확인
                if (remaining <= 0) {
                    System.out.println("남은 수량이 없습니다.");
                    conn.rollback();
                    return;
                }

                // 7. 남은 수량 1 감소
                String updateSql = """
                        UPDATE reward
                        SET remaining_quantity = remaining_quantity - 1
                        WHERE reward_id = ?
                          AND project_id = ?
                          AND remaining_quantity > 0
                        """;

                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, rewardId);
                    ps.setInt(2, projectId);

                    int updated = ps.executeUpdate();

                    if (updated == 0) {
                        throw new SQLException("수량 차감에 실패했습니다.");
                    }
                }

                // 8. 변경 사항 확정
                conn.commit();

                System.out.println("\n수량 차감 성공!");
                System.out.println("차감 전 수량: " + remaining);
                System.out.println("차감 후 수량: " + (remaining - 1));

            } catch (Exception e) {
                conn.rollback();
                throw e;

            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}