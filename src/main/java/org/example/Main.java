package org.example;

import org.example.persistence.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


/*
 * ==================================================================
 *  변경 내역 (원본 Main.java 대비, 11/02 DB 검사 기준)
 *  [수정] = 원본에 있던 것을 고침 / [추가] = 원본에 없던 것을 새로 만듦
 * ==================================================================
 *
 *  [공통]
 *   - [추가] DBConnection.java (persistence 패키지): 원본이 import했지만 프로젝트에 없던 클래스.
 *            config/db.properties의 접속 정보를 읽는다.
 *   - [추가] BusinessException: 입력 오류·업무 규칙 위반을 DB 오류와 구분해 메시지만 출력
 *   - [추가] readLine / readInt / readAmount / selectCategory / countRows 입력·조회 도우미
 *            (원본은 Integer.parseInt를 감싸지 않아 문자 입력 시 프로그램이 종료됐음)
 *   - [수정] main의 catch: catch(Exception) 하나로 받고 instanceof로 구분
 *            (catch(BusinessException)을 따로 쓰면 테스트를 모두 주석 처리했을 때 컴파일 오류)
 *   - [추가] 상수 MAKER2_ID, CATEGORIES(가전/식품), PUBLIC_STATUS, CLOSED_STATUS
 *   - [삭제] findProjectId, findRewardId, insertTestUser (새 initDB에서 쓰지 않음)
 *
 *  [initDB]
 *   - [수정] 원본: 회원 3, 프로젝트 1(카테고리 '테스트'), 리워드 1, 후원 0건
 *            → 검사 조건(한 메이커 2개 이상 + 후원 기록, 전체 5개 이상) 미충족
 *   - [추가] resetDB(): 실행할 때마다 8개 테이블 TRUNCATE, ID를 1부터 다시 시작
 *   - [수정] 회원 8, 배송지 5, 프로젝트 6(가전 3/식품 3, 진행중 3/성공 1/실패 1/대기 1),
 *            리워드 12(프로젝트마다 한정 1 + 무제한 1), 후원 11, 승인 이력, 좋아요 10, 리뷰 2
 *   - [추가] insertUser / insertAddress / insertProject / insertReward / insertApproval /
 *            insertLike / insertReview / insertFunding 삽입 도우미
 *
 *  [test01 펀딩 현황]  [수정] 총 금액만 → 리워드별 신청 수량/잔여 수량/서포터/배송지 추가
 *  [test02 프로젝트 등록]  [수정] '신청' 이력을 넣지 않음(신청은 test03에서), 진행 기간 입력 추가
 *  [test03 리워드 등록 후 신청]
 *   - [수정] 리워드마다 '신청' 이력이 쌓이던 문제 → 리워드 2개 이상일 때 1회만 신청
 *   - [수정] 진행중 프로젝트가 목록에 나와 '대기'로 되돌아가던 문제 → '대기'만 표시
 *   - [추가] 리워드 연속 입력, 거절된 프로젝트는 '재신청', 목록에 목표 금액·리워드 수 표시
 *  [test04 승인]  [수정] 조건부 UPDATE(status='대기'일 때만)로 한 번만 승인, 목록에 메이커·리워드 수
 *  [test05 개인정보 수정]
 *   - [수정] 전화번호 정규식이 "((((((((((" 도 통과하던 문제 → 01X-XXXX-XXXX 형식만 허용
 *   - [추가] 현재 값 표시, Enter면 기존 값 유지, UNIQUE 위반도 안내 문구로 처리
 *  [test06 프로젝트 보기]  [추가] 모금액, 달성률(%), 좋아요 수, 리뷰 수·평균 별점·리뷰 내용
 *  [test07 필터링]
 *   - [수정] 진행/마감 조건이 없던 문제 → 카테고리, 진행/마감 선택 추가
 *   - [수정] 카테고리 자유 입력 LIKE("식"만 쳐도 검색) → 번호 선택 + = 비교
 *   - [수정] 프로젝트명 검색은 선택 조건으로 유지, 선택한 조건만 WHERE에 붙이는 동적 쿼리
 *  [test08 리워드 신청]
 *   - [수정] 같은 프로젝트에 여러 번 후원되던 문제 → 1인 1프로젝트 1후원 검사
 *   - [추가] 신청 가능한 리워드 목록, 마감일 검사, 기본 배송지 저장, applyReward()로 로직 분리
 *  [test09 리뷰]
 *   - [수정] 리뷰 가능한 데이터가 없어 항상 거부되던 문제 → initDB에 성공 프로젝트 + 후원 추가
 *   - [추가] 작성 가능한 프로젝트를 리워드명과 함께 표시, 기존 리뷰는 '수정'으로 처리
 *   - [수정] deprecated 예정인 ON DUPLICATE KEY ... VALUES() → 존재 여부로 INSERT/UPDATE 분기
 *  [test10 동시성 제어]
 *   - [수정] 재고만 줄고 펀딩 기록이 남지 않던 문제 → applyReward()로 기록 + 재고 감소
 *   - [수정] 메서드 안의 new Scanner(System.in) + nextInt() 제거 (입력 꼬임, 문자 입력 시 종료)
 *   - [추가] 서포터 2명으로 성공/실패를 자동 시연, 신청 전후 잔여 수량·펀딩 기록 수 출력
 * ==================================================================
 */

public class Main {
    private static final Scanner sc = new Scanner(System.in);

    private static final String MAKER_ID = "test_maker";
    // [추가] 두 번째 메이커 (initDB 더미 데이터용)
    private static final String MAKER2_ID = "test_maker2";
    private static final String SUPPORTER_ID = "test_supporter";
    private static final String ADMIN_ID = "test_admin";

    // 카테고리는 가전, 식품 2개
    // [추가]
    private static final String[] CATEGORIES = {"가전", "식품"};

    // 서포터에게 공개되는 프로젝트 상태 (승인 전 '대기'는 제외)
    // [추가] 상태 목록을 한 곳에서 관리
    private static final String PUBLIC_STATUS = "('진행중', '성공', '실패', '종료')";
    private static final String CLOSED_STATUS = "('성공', '실패', '종료')";

    // [수정] catch 구조 변경 (아래 catch 주석 참고)
    public static void main(String[] args) {
        try {
            // 1) initDB만 주석 해제하고 실행 → 2) initDB 다시 주석 → 3) 테스트를 하나씩 실행
            // initDB는 기존 데이터를 모두 지우므로 테스트 도중에는 주석 상태로 둘 것
            //initDB();                       // 검사에 필요한 사전 데이터를 insert하는 메소드

            // 검사할 항목 하나만 주석 해제하여 실행
            //test01ViewFundingStatus();      // MAKER     - 프로젝트별 펀딩 현황 보기
            //test02RegisterProject();        // MAKER     - 프로젝트 기본 정보 등록
           //test03RegisterReward();         // MAKER     - 리워드 등록 및 프로젝트 신청
            //test04ApproveProject();         // ADMIN     - 프로젝트 등록 승인
            //test05UpdateProfile();          // SUPPORTER - 개인정보 수정
            //test06ViewProjects();           // SUPPORTER - 프로젝트 보기
            //test07FilterProjects();         // SUPPORTER - 프로젝트 필터링
            //test08ApplyReward();            // SUPPORTER - 리워드 신청
            //test09RegisterReview();         // SUPPORTER - 리뷰 및 별점 등록
            //test10ConcurrencyControl();     // SUPPORTER - 제공 가능 수량 동시성 제어

            System.out.println("테스트가 종료되었습니다.");

        } catch (Exception e) {
            // 입력 오류나 업무 규칙 위반은 메시지만 출력
            // (catch (BusinessException e)로 따로 쓰면 테스트를 모두 주석 처리했을 때 컴파일 오류가 남)
            if (e instanceof BusinessException) {
                System.out.println("요청 실패: " + e.getMessage());
            } else {
                System.out.println("테스트 오류: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // 업무 규칙 위반(수량 부족, 중복 후원 등)을 DB 오류와 구분하기 위한 예외
    // [추가]
    private static class BusinessException extends Exception {
        BusinessException(String message) {
            super(message);
        }
    }


    // ==================================================
    // 공통 도우미
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

    // [추가] COUNT(*) 결과 하나를 돌려주는 도우미
    private static int countRows(Connection conn, String sql, Object... values)
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

    // [추가] 입력 도우미
    private static String readLine(String prompt) {
        System.out.print(prompt);
        return sc.nextLine().trim();
    }

    // [추가] 숫자가 아니면 BusinessException (원본은 NumberFormatException으로 종료)
    private static int readInt(String prompt) throws BusinessException {
        String input = readLine(prompt);

        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new BusinessException("숫자를 입력해야 합니다: " + input);
        }
    }

    // [추가] 금액 입력: 1원 이상의 정수만 허용
    private static BigDecimal readAmount(String prompt) throws BusinessException {
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

    // 1. 가전 / 2. 식품, allowAll이면 Enter로 전체 선택 (null 반환)
    // [추가] 카테고리를 번호로 선택 (원본 test07은 자유 입력 LIKE)
    private static String selectCategory(boolean allowAll) throws BusinessException {
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

    // ==================================================
    // 사전 데이터 생성
    // ==================================================
    //
    // 실행할 때마다 모든 테이블을 비우고 ID를 1부터 다시 시작한다.
    //
    // 회원 ID      1 test_maker, 2 test_maker2,
    //              3 test_supporter, 4~7 test_supporter2~5, 8 test_admin
    // 프로젝트 ID  1 제주 감귤 정기배송 (식품, 진행중)  test_maker
    //              2 무선 미니 가습기   (가전, 진행중)  test_maker   ← test08, test10 시연용 (후원 없음)
    //              3 수제 그래놀라      (식품, 성공)    test_maker   ← test09 리뷰 시연용
    //              4 휴대용 블렌더      (가전, 진행중)  test_maker2
    //              5 전통 한과 세트     (식품, 실패)    test_maker2
    //              6 스마트 전기포트    (가전, 대기)    test_maker2  ← test04 승인 시연용
    // 리워드       프로젝트마다 2개 (수량 한정 1 + 수량 제한 없음 1)
    // 후원         11건, 1인 1프로젝트 1후원, 배송지 포함

    // [추가] resetDB가 비우는 테이블 (외래 키 자식 → 부모 순)
    private static final String[] RESET_TABLES = {
            "approval_history", "project_review", "project_like",
            "funding", "reward", "project", "shipping_address", "users"
    };

    // [수정] 원본: 프로젝트 1개, 후원 0건 → 검사 조건에 맞는 더미 데이터로 전면 교체
    private static void initDB() throws Exception {
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

    // 모든 테이블을 비우고 AUTO_INCREMENT를 1로 되돌림
    // TRUNCATE는 외래 키가 걸린 테이블에 바로 쓸 수 없으므로 잠시 FK 검사를 끈다.
    // [추가] initDB 실행 전 기존 데이터 초기화
    private static void resetDB() throws SQLException {
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

    // [추가] 원본 insertTestUser 대체 (생성된 ID를 바로 반환)
    private static long insertUser(
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

    // [추가] test01 배송지 출력, test08 배송지 저장용
    private static int insertAddress(
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

    // [추가]
    private static int insertProject(
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

    // totalQuantity, remainingQuantity가 null이면 수량 제한 없음
    // [추가] test03에서도 재사용
    private static int insertReward(
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

    // [추가] test03, test04에서도 재사용
    private static void insertApproval(
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

    // [추가] test06 좋아요 수 표시용 더미 데이터
    private static void insertLike(Connection conn, int projectId, long supporterId)
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

    // [추가] test06 리뷰 표시용 더미 데이터
    private static void insertReview(
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

    // 후원 금액 = 리워드 금액 x 수량
    // [추가]
    private static void insertFunding(
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

    // ==================================================
    // test01: 프로젝트별 펀딩 현황
    // 프로젝트별 총 펀딩 금액, 리워드별 신청 수량/잔여 수량/서포터 및 배송지
    // ==================================================

    // [수정] 원본은 프로젝트별 총 금액만 출력
    //        → 리워드별 신청 수량/잔여 수량, 서포터와 배송지 추가 (검사 문서 1번 요구)
    private static void test01ViewFundingStatus() throws Exception {
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

    // ==================================================
    // test02: 프로젝트 기본 정보 등록
    // 승인 신청은 리워드를 등록한 뒤 test03에서 한다.
    // ==================================================

    // [수정] 원본은 등록과 동시에 '신청' 이력을 넣어 리워드 없는 프로젝트가 승인 목록에 올라감
    //        → 프로젝트만 '대기'로 저장, 신청은 test03에서
    // [추가] 진행 기간(일) 입력, 프로젝트명 길이 검사
    private static void test02RegisterProject() throws Exception {
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

    // ==================================================
    // test03: 리워드 등록 후 프로젝트 승인 신청
    // 리워드를 여러 개 입력받고, 리워드가 2개 이상이면 신청 이력을 1번만 남긴다.
    // ==================================================

    // [수정] 원본은 리워드 1개마다 '신청' 이력 추가, 진행중 프로젝트를 '대기'로 되돌림
    //        → '대기' 프로젝트만 대상, 리워드 2개 이상일 때 신청 1회
    // [추가] 리워드 연속 입력, 거절 시 '재신청', 목록에 목표 금액 표시
    private static void test03RegisterReward() throws Exception {
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
    // 가장 최근 승인 이력의 action, 이력이 없으면 null
    // [추가] test03에서 중복 신청 / 재신청 판단용
    private static String findLastApprovalAction(Connection conn, int projectId)
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

    // ==================================================
    // test04: 메이커의 프로젝트 등록 신청 승인
    // ==================================================

    // [수정] 원본은 approval_history 행을 잠그고 project_status를 확인하지 않음
    //        → status='대기'일 때만 바뀌는 조건부 UPDATE로 한 번만 승인
    // [추가] 목록에 메이커 이름, 리워드 개수 표시
    private static void test04ApproveProject() throws Exception {
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

    // ==================================================
    // test05: (MyPage) 서포터 개인정보 수정
    // 현재 값을 보여주고, Enter를 누르면 기존 값 유지
    // ==================================================

    // [수정] 원본 정규식은 "((((((((((" 도 통과 → 01X-XXXX-XXXX 형식만 허용
    // [추가] 현재 값 표시, Enter면 기존 값 유지, UNIQUE 위반 예외 처리
    private static void test05UpdateProfile() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            long supporterId = findUserId(conn, SUPPORTER_ID);

            String currentName;
            String currentPhone;
            String currentBio;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name, phone_number, bio FROM users WHERE id = ?")) {
                ps.setLong(1, supporterId);

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    currentName = rs.getString("name");
                    currentPhone = rs.getString("phone_number");
                    currentBio = rs.getString("bio");
                }
            }

            System.out.println("\n===== 내 정보 (Enter는 기존 값 유지) =====");

            String name = readLine("이름 [" + currentName + "]: ");
            String phone = readLine("전화번호 [" + currentPhone + "]: ");
            String bio = readLine("자기소개 [" + (currentBio == null ? "" : currentBio) + "]: ");

            if (name.isEmpty()) {
                name = currentName;
            }
            if (phone.isEmpty()) {
                phone = currentPhone;
            }
            if (bio.isEmpty()) {
                bio = currentBio;
            }

            if (name.length() > 50) {
                throw new BusinessException("이름은 50자 이하로 입력하세요.");
            }

            if (!phone.matches("01[0-9]-\\d{3,4}-\\d{4}")) {
                throw new BusinessException("전화번호는 010-0000-0000 형식으로 입력하세요.");
            }

            if (exists(conn,
                    "SELECT 1 FROM users WHERE phone_number = ? AND id <> ?",
                    phone, supporterId)) {
                throw new BusinessException("이미 사용 중인 전화번호입니다.");
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
            } catch (SQLIntegrityConstraintViolationException e) {
                // 확인과 수정 사이에 다른 회원이 같은 번호를 등록한 경우
                throw new BusinessException("이미 사용 중인 전화번호입니다.");
            }

            System.out.println("개인정보 수정 완료.");
        }
    }

    // ==================================================
    // test06: 프로젝트 보기 (기본)
    // 승인 전(대기), 진행 중, 마감(성공/실패/종료) 프로젝트 전체
    // 모금액, 달성률, 좋아요 수, 리뷰(개수, 평균 별점, 내용)
    //
    // 좋아요와 리뷰는 스칼라 서브쿼리로 따로 센다.
    // funding, project_like, project_review를 한 번에 JOIN하면
    // 후원 수 x 좋아요 수 x 리뷰 수만큼 행이 늘어나 SUM(모금액)이 부풀기 때문.
    // ==================================================

    // [추가] 모금액, 달성률(%), 좋아요 수, 리뷰 수·평균 별점·리뷰 내용
    private static void test06ViewProjects() throws Exception {
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

            while (rs.next()) {
                found = true;

                System.out.println("\nID: " + rs.getInt("project_id"));
                System.out.println("프로젝트명: " + rs.getString("project_name"));
                String status = rs.getString("project_status");
                System.out.println("상태: " + ("대기".equals(status) ? "대기 (승인 전)" : status));
                System.out.println("분류: " + rs.getString("category"));
                System.out.println("메이커: " + rs.getString("maker_name"));
                System.out.println("설명: " + rs.getString("description"));
                System.out.printf("모금액: %,.0f원 / 목표 %,.0f원 (%.1f%%)%n",
                        rs.getBigDecimal("total_funding"),
                        rs.getBigDecimal("target_amount"),
                        rs.getBigDecimal("achievement_rate"));
                System.out.println("기간: " + rs.getDate("start_date")
                        + " ~ " + rs.getDate("end_date"));

                int reviewCount = rs.getInt("review_count");
                System.out.print("좋아요: " + rs.getInt("like_count") + "개"
                        + " | 리뷰: " + reviewCount + "개");

                if (reviewCount > 0) {
                    System.out.print(" (평균 ★" + rs.getBigDecimal("avg_rating") + ")");
                }
                System.out.println();

                reviewPs.setInt(1, rs.getInt("project_id"));

                try (ResultSet rr = reviewPs.executeQuery()) {
                    while (rr.next()) {
                        System.out.println("  - " + rr.getString("name")
                                + " ★" + rr.getInt("rating")
                                + " \"" + rr.getString("content") + "\"");
                    }
                }
            }

            if (!found) {
                System.out.println("조회할 프로젝트가 없습니다.");
            }
        }
    }

    // ==================================================
    // test07: 프로젝트 필터링 (카테고리, 진행/마감, 프로젝트명)
    // 선택한 조건만 WHERE에 붙이는 동적 쿼리
    // ==================================================

    // [수정] 원본은 '진행중' 고정 + 이름/카테고리 LIKE 검색 (진행/마감 조건 없음)
    //        → 카테고리(번호 선택), 진행/마감, 프로젝트명(선택) 동적 쿼리
    private static void test07FilterProjects() throws Exception {
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

    // ==================================================
    // test08: 리워드 신청
    // 하나의 프로젝트에는 하나의 리워드(후원)만 신청 가능
    // ==================================================

    // [수정] 원본은 같은 프로젝트에 여러 번 후원 가능, 문자 입력 시 종료
    //        → 신청 로직을 applyReward()로 옮기고 1인 1프로젝트 1후원 검사
    // [추가] 신청 가능한 리워드 목록 출력
    private static void test08ApplyReward() throws Exception {
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

    // 진행 중인 프로젝트와 리워드 목록 출력
    // [추가]
    private static void printOpenRewards(Connection conn) throws SQLException {
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

    // 리워드 신청 한 건을 하나의 트랜잭션으로 처리 (test08, test10 공용)
    //   1. 진행 중인 프로젝트인지 확인
    //   2. 이미 이 프로젝트에 후원했는지 확인 (1인 1프로젝트 1후원)
    //   3. 리워드 행 잠금 후 잔여 수량 확인
    //   4. 수량 한정 리워드면 잔여 수량 차감
    //   5. 펀딩 기록 저장 (기본 배송지 포함)
    // 하나라도 실패하면 롤백되어 펀딩 기록과 재고 모두 변하지 않는다.
    // [추가] 원본 test08의 신청 로직 + 1인 1후원, 마감일 검사, 기본 배송지 저장

    private static BigDecimal applyReward(
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

    // ==================================================
    // test09: 리뷰와 별점(1~5점) 등록 - 리워드명/별점/리뷰
    // 성공(종료)한 프로젝트에 후원한 서포터만 작성 가능
    // ==================================================

    // [수정] 원본은 성공 프로젝트가 없어 항상 거부됨 (initDB에서 해결)
    // [수정] ON DUPLICATE KEY ... VALUES() → 리뷰 존재 여부로 INSERT/UPDATE 분기
    // [추가] 작성 가능한 프로젝트를 리워드명과 함께 표시 (검사 문서 9번: 리워드명/별점/리뷰)
    private static void test09RegisterReview() throws Exception {
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

    // ==================================================
    // test10: 제공 가능 수량 동시성 제어
    // 수량이 남았을 때: 펀딩 기록이 남고 재고가 줄어든다.
    // 수량이 부족할 때: 펀딩 기록도 재고도 변하지 않는다.
    // 리워드 행을 SELECT ... FOR UPDATE로 잠그기 때문에
    // 동시에 신청이 들어와도 한 트랜잭션씩 순서대로 재고를 확인한다.
    // ==================================================

    // [수정] 원본은 재고만 줄이고 funding 기록을 남기지 않음 → applyReward()로 기록 + 재고 감소
    // [수정] 메서드 안의 new Scanner(System.in) + nextInt() 제거
    // [추가] 서포터 2명으로 성공(기록 +1, 재고 감소) / 실패(변화 없음)를 한 번에 시연
    private static void test10ConcurrencyControl() throws Exception {
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
