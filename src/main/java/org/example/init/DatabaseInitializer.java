package org.example.init;

import org.example.persistence.DBConnection;
import org.example.common.AppSupport;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import static org.example.common.AppSupport.*;

public class DatabaseInitializer {
    private static final String[] RESET_TABLES = {
        "approval_history", "project_review", "project_like",
        "funding", "reward", "project", "shipping_address", "users"
    };

    public static void initDB() throws Exception {
        resetDB();

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. 회원
                long maker1 = insertUser(conn, MAKER_ID, "테스트 메이커",
                        "010-1111-1111", "MAKER");
                long maker2 = insertUser(conn, MAKER2_ID, "김메이커",
                        "010-1111-2222", "MAKER");

                long s1 = insertUser(conn, SUPPORTER_ID, "테스트 서포터",
                        "010-2222-2222", "SUPPORTER");
                long s2 = insertUser(conn, "test_supporter2", "이서포터",
                        "010-2222-3333", "SUPPORTER");
                long s3 = insertUser(conn, "test_supporter3", "박서포터",
                        "010-2222-4444", "SUPPORTER");
                long s4 = insertUser(conn, "test_supporter4", "최서포터",
                        "010-2222-5555", "SUPPORTER");
                long s5 = insertUser(conn, "test_supporter5", "정서포터",
                        "010-2222-6666", "SUPPORTER");

                long admin = insertUser(conn, ADMIN_ID, "테스트 관리자",
                        "010-3333-3333", "ADMIN");

                // 2. 서포터 기본 배송지
                int a1 = insertAddress(conn, s1, "테스트 서포터", "010-2222-2222",
                        "39177", "경북 구미시 대학로 61", "디지털관 101호");
                int a2 = insertAddress(conn, s2, "이서포터", "010-2222-3333",
                        "06236", "서울 강남구 테헤란로 152", "12층");
                int a3 = insertAddress(conn, s3, "박서포터", "010-2222-4444",
                        "48058", "부산 해운대구 센텀중앙로 79", "301동 502호");
                int a4 = insertAddress(conn, s4, "최서포터", "010-2222-5555",
                        "41940", "대구 중구 동덕로 130", "2층");
                int a5 = insertAddress(conn, s5, "정서포터", "010-2222-6666",
                        "61452", "광주 동구 필문대로 309", "본관 3층");

                LocalDate today = LocalDate.now();

                // ---------- P1. 제주 감귤 정기배송 (식품, 진행중) ----------
                int p1 = insertProject(conn, maker1, "제주 감귤 정기배송", "식품",
                        "제주에서 갓 수확한 감귤을 보내드립니다.",
                        "1000000", today.minusDays(5), today.plusDays(25), "진행중");
                insertApproval(conn, p1, maker1, "신청");
                insertApproval(conn, p1, admin, "승인");

                int r1 = insertReward(conn, p1, "감귤 3kg",
                        "제주 감귤 3kg 1박스", "20000", 50, 47);
                int r2 = insertReward(conn, p1, "감귤 5kg",
                        "제주 감귤 5kg 1박스", "30000", null, null);

                insertFunding(conn, s1, p1, r1, a1, "20000", 2, today.minusDays(4));
                insertFunding(conn, s2, p1, r2, a2, "30000", 1, today.minusDays(3));
                insertFunding(conn, s3, p1, r1, a3, "20000", 1, today.minusDays(2));

                // ---------- P2. 무선 미니 가습기 (가전, 진행중) ----------
                // 후원 기록 없음: test08, test10 시연용
                int p2 = insertProject(conn, maker1, "무선 미니 가습기", "가전",
                        "책상 위에 두는 USB 충전식 무선 가습기입니다.",
                        "2000000", today.minusDays(3), today.plusDays(27), "진행중");
                insertApproval(conn, p2, maker1, "신청");
                insertApproval(conn, p2, admin, "승인");

                insertReward(conn, p2, "얼리버드 가습기",
                        "선착순 3명 한정 할인가", "39000", 3, 3);
                insertReward(conn, p2, "가습기 기본",
                        "무선 미니 가습기 1대", "49000", null, null);

                // ---------- P3. 수제 그래놀라 (식품, 성공) ----------
                // 목표 500,000원 / 모금 545,000원
                int p3 = insertProject(conn, maker1, "수제 그래놀라", "식품",
                        "설탕을 줄인 수제 그래놀라입니다.",
                        "500000", today.minusDays(40), today.minusDays(10), "성공");
                insertApproval(conn, p3, maker1, "신청");
                insertApproval(conn, p3, admin, "승인");

                int r5 = insertReward(conn, p3, "그래놀라 2봉",
                        "그래놀라 300g 2봉", "15000", 30, 24);
                int r6 = insertReward(conn, p3, "그래놀라 5봉",
                        "그래놀라 300g 5봉", "35000", null, null);

                insertFunding(conn, s1, p3, r6, a1, "35000", 4, today.minusDays(38));
                insertFunding(conn, s2, p3, r6, a2, "35000", 5, today.minusDays(35));
                insertFunding(conn, s3, p3, r5, a3, "15000", 6, today.minusDays(30));
                insertFunding(conn, s4, p3, r6, a4, "35000", 4, today.minusDays(20));

                // ---------- P4. 휴대용 블렌더 (가전, 진행중) ----------
                int p4 = insertProject(conn, maker2, "휴대용 블렌더", "가전",
                        "어디서나 쓰는 충전식 휴대용 블렌더입니다.",
                        "1500000", today.minusDays(10), today.plusDays(20), "진행중");
                insertApproval(conn, p4, maker2, "신청");
                insertApproval(conn, p4, admin, "승인");

                int r7 = insertReward(conn, p4, "블렌더 얼리버드",
                        "선착순 20명 한정", "45000", 20, 19);
                int r8 = insertReward(conn, p4, "블렌더 기본",
                        "휴대용 블렌더 1대", "59000", null, null);

                insertFunding(conn, s1, p4, r7, a1, "45000", 1, today.minusDays(9));
                insertFunding(conn, s4, p4, r8, a4, "59000", 1, today.minusDays(7));

                // ---------- P5. 전통 한과 세트 (식품, 실패) ----------
                // 목표 3,000,000원 / 모금 90,000원
                int p5 = insertProject(conn, maker2, "전통 한과 세트", "식품",
                        "명절 선물용 전통 한과 세트입니다.",
                        "3000000", today.minusDays(45), today.minusDays(15), "실패");
                insertApproval(conn, p5, maker2, "신청");
                insertApproval(conn, p5, admin, "승인");

                int r9 = insertReward(conn, p5, "한과 소형 세트",
                        "10세트 한정", "25000", 10, 8);
                int r10 = insertReward(conn, p5, "한과 대형 세트",
                        "한과 대형 세트 1개", "40000", null, null);

                insertFunding(conn, s3, p5, r9, a3, "25000", 2, today.minusDays(40));
                insertFunding(conn, s5, p5, r10, a5, "40000", 1, today.minusDays(30));

                // ---------- P6. 스마트 전기포트 (가전, 대기) ----------
                // 승인 신청만 된 상태: test04 승인 시연용
                int p6 = insertProject(conn, maker2, "스마트 전기포트", "가전",
                        "온도를 1도 단위로 맞추는 전기포트입니다.",
                        "1000000", today, today.plusDays(30), "대기");
                insertApproval(conn, p6, maker2, "신청");

                insertReward(conn, p6, "전기포트 얼리버드",
                        "선착순 15명 한정", "55000", 15, 15);
                insertReward(conn, p6, "전기포트 기본",
                        "스마트 전기포트 1대", "65000", null, null);

                // ---------- 좋아요 (1인 1프로젝트 1회) ----------
                insertLike(conn, p1, s1);
                insertLike(conn, p1, s2);
                insertLike(conn, p1, s4);
                insertLike(conn, p2, s3);
                insertLike(conn, p2, s5);
                insertLike(conn, p3, s2);
                insertLike(conn, p3, s3);
                insertLike(conn, p3, s4);
                insertLike(conn, p4, s2);
                insertLike(conn, p5, s5);

                // ---------- 리뷰 (성공한 P3에 후원한 서포터) ----------
                // test_supporter의 리뷰는 넣지 않음: test09에서 '등록'을 시연하기 위해
                insertReview(conn, p3, s2, 5, "아침마다 우유에 타 먹어요. 재구매 의사 있습니다.");
                insertReview(conn, p3, s4, 4, "맛있는데 조금 달아요.");

                conn.commit();
                System.out.println("테스트용 사전 데이터 준비 완료.");

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static void resetDB() throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement()) {

            st.execute("SET FOREIGN_KEY_CHECKS = 0");

            try {
                for (String table : RESET_TABLES) {
                    st.executeUpdate("TRUNCATE TABLE " + table);
                }
            } finally {
                st.execute("SET FOREIGN_KEY_CHECKS = 1");
            }

            System.out.println("기존 데이터를 모두 초기화했습니다.");
        }
    }

    public static long insertUser(
            Connection conn, String loginId, String name,
            String phone, String role) throws SQLException {

        String sql = """
                INSERT INTO users
                (login_id, password, name, phone_number, role, status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """;

        try (PreparedStatement ps = conn.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, loginId);

            // 테스트 전용 값이며 실제 로그인용 비밀번호 해시가 아닙니다.
            ps.setString(2, "TEST_ONLY");

            ps.setString(3, name);
            ps.setString(4, phone);
            ps.setString(5, role);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public static int insertAddress(
            Connection conn, long userId, String recipientName,
            String recipientPhone, String postalCode,
            String baseAddress, String detailAddress) throws SQLException {

        String sql = """
                INSERT INTO shipping_address
                (user_id, address_name, recipient_name, recipient_phone,
                 postal_code, base_address, detail_address, is_default)
                VALUES (?, '집', ?, ?, ?, ?, ?, TRUE)
                """;

        try (PreparedStatement ps = conn.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, recipientName);
            ps.setString(3, recipientPhone);
            ps.setString(4, postalCode);
            ps.setString(5, baseAddress);
            ps.setString(6, detailAddress);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static int insertProject(
            Connection conn, long makerId, String name, String category,
            String description, String targetAmount,
            LocalDate startDate, LocalDate endDate, String status)
            throws SQLException {

        String sql = """
                INSERT INTO project
                (maker_id, project_name, category, description,
                 target_amount, start_date, end_date, project_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, makerId);
            ps.setString(2, name);
            ps.setString(3, category);
            ps.setString(4, description);
            ps.setBigDecimal(5, new BigDecimal(targetAmount));
            ps.setDate(6, Date.valueOf(startDate));
            ps.setDate(7, Date.valueOf(endDate));
            ps.setString(8, status);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("프로젝트 ID를 가져오지 못했습니다.");
                }
                return rs.getInt(1);
            }
        }
    }

    public static void insertLike(Connection conn, int projectId, long supporterId)
            throws SQLException {

        String sql = """
                INSERT INTO project_like (project_id, supporter_id)
                VALUES (?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            ps.setLong(2, supporterId);
            ps.executeUpdate();
        }
    }

    public static void insertReview(
            Connection conn, int projectId, long supporterId,
            int rating, String content) throws SQLException {

        String sql = """
                INSERT INTO project_review
                (project_id, supporter_id, rating, content)
                VALUES (?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            ps.setLong(2, supporterId);
            ps.setInt(3, rating);
            ps.setString(4, content);
            ps.executeUpdate();
        }
    }

    public static void insertFunding(
            Connection conn, long supporterId, int projectId, int rewardId,
            int addressId, String rewardAmount, int quantity,
            LocalDate fundingDate) throws SQLException {

        String sql = """
                INSERT INTO funding
                (supporter_id, project_id, reward_id, address_id,
                 funding_amount, quantity, funding_date)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, supporterId);
            ps.setInt(2, projectId);
            ps.setInt(3, rewardId);
            ps.setInt(4, addressId);
            ps.setBigDecimal(5, new BigDecimal(rewardAmount)
                    .multiply(BigDecimal.valueOf(quantity)));
            ps.setInt(6, quantity);
            ps.setTimestamp(7, Timestamp.valueOf(fundingDate.atTime(12, 0)));
            ps.executeUpdate();
        }
    }
}
