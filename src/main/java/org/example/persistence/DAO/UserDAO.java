package org.example.persistence.DAO;

import org.example.persistence.DBConnection;
import org.example.persistence.DTO.UserDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserDAO {

    public boolean existsByLoginId(String loginId) throws Exception {
        String sql = "SELECT 1 FROM users WHERE login_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loginId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean existsByPhoneNumber(String phoneNumber)
            throws Exception {
        String sql = "SELECT 1 FROM users WHERE phone_number = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phoneNumber);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void insert(UserDTO user) throws Exception {
        String sql = """
                INSERT INTO users
                (login_id, password, name, phone_number, role)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getLoginId());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getName());
            ps.setString(4, user.getPhoneNumber());
            ps.setString(5, user.getRole());

            ps.executeUpdate();
        }
    }

    public UserDTO findByLoginId(String loginId) throws Exception {
        String sql = """
                SELECT id, login_id, password, name,
                       phone_number, role, status
                FROM users
                WHERE login_id = ?
                """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loginId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return new UserDTO(
                        rs.getLong("id"),
                        rs.getString("login_id"),
                        rs.getString("password"),
                        rs.getString("name"),
                        rs.getString("phone_number"),
                        rs.getString("role"),
                        rs.getString("status")
                );
            }
        }
    }
}