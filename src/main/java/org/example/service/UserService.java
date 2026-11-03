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

public class UserService {
    public static void test05UpdateProfile() throws Exception {
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
}
