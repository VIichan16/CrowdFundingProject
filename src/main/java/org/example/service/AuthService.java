package org.example.service;

import org.example.dto.LoginUser;
import org.example.persistence.DAO.UserDAO;
import org.example.persistence.DTO.UserDTO;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** 로그인 및 일반 회원가입 처리 */
public class AuthService {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final UserDAO userDAO = new UserDAO();

    public static LoginUser login(String loginId, String password) throws Exception {
        UserDTO user = userDAO.findByLoginId(loginId);

        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            return null;
        }

        if (!verifyPassword(password, user.getPassword())) {
            return null;
        }

        // 기존 테스트 계정(TEST_ONLY 등)의 평문 비밀번호는 로그인 성공 후 해시로 전환
        if (!user.getPassword().startsWith("pbkdf2$")) {
            userDAO.updatePassword(loginId, hashPassword(password));
        }

        return new LoginUser(user.getId(), user.getLoginId(),
                user.getName(), user.getRole());
    }

    /** MAKER 또는 SUPPORTER만 가입 가능. ADMIN은 서버 측에서도 차단한다. */
    public static void register(String loginId, String password,
                                 String name, String phoneNumber,
                                 String role) throws Exception {
        loginId = loginId == null ? "" : loginId.trim();
        name = name == null ? "" : name.trim();
        phoneNumber = phoneNumber == null ? "" : phoneNumber.trim();

        if (!loginId.matches("[a-zA-Z0-9_]{4,20}")) {
            throw new IllegalArgumentException("아이디는 영문, 숫자, 밑줄(_)로 4~20자 입력하세요.");
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("비밀번호는 8~72자로 입력하세요.");
        }
        if (name.isBlank() || name.length() > 50) {
            throw new IllegalArgumentException("이름은 1~50자로 입력하세요.");
        }
        String digits = phoneNumber.replaceAll("[- ]", "");
        if (!digits.matches("[0-9]{10,11}")) {
            throw new IllegalArgumentException("전화번호를 숫자 10~11자리로 입력하세요. 예: 010-1234-5678");
        }
        if (!"MAKER".equals(role) && !"SUPPORTER".equals(role)) {
            throw new IllegalArgumentException("창작자 또는 후원자만 가입할 수 있습니다.");
        }

        if (userDAO.existsByLoginId(loginId)) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (userDAO.existsByPhoneNumber(digits)) {
            throw new IllegalArgumentException("이미 등록된 전화번호입니다.");
        }

        // 휴대전화 11자리는 010-1234-5678 형식으로 통일해 저장
        if (digits.length() == 11) {
            phoneNumber = digits.substring(0, 3) + "-"
                    + digits.substring(3, 7) + "-" + digits.substring(7);
        } else {
            phoneNumber = digits.substring(0, 3) + "-"
                    + digits.substring(3, 6) + "-" + digits.substring(6);
        }

        userDAO.insert(new UserDTO(0, loginId, hashPassword(password),
                name, phoneNumber, role, "ACTIVE"));
    }

    private static String hashPassword(String password) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password.toCharArray(), salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    private static boolean verifyPassword(String password, String stored)
            throws Exception {
        if (password == null || stored == null) return false;

        // 기존 테스트 데이터와의 호환. 새로 가입하는 계정은 해시만 저장한다.
        if (!stored.startsWith("pbkdf2$")) {
            return MessageDigest.isEqual(
                    password.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    stored.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        String[] parts = stored.split("\\$", -1);
        if (parts.length != 4) return false;
        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        byte[] actual = derive(password.toCharArray(), salt, iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations)
            throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
