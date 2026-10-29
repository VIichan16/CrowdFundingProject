package org.example.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Main의 test01~test10에서 쓰는 DB 연결 클래스.
 * 접속 정보는 src/main/resources/config/db.properties에서 읽는다.
 * (DRIVER_CLASS, DB_CONNECTION_URL, DB_USER, DB_PWD)
 */
public class DBConnection {
    private static final Properties props = new Properties();

    static {
        try (InputStream in = DBConnection.class.getClassLoader()
                .getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                        "src/main/resources/db.properties 파일을 찾을 수 없습니다.");
            }

            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            Class.forName(props.getProperty("DRIVER_CLASS"));

            // 실제로 읽은 파일 위치 (src가 아니라 build/out 폴더의 복사본일 수 있음)
            System.out.println("DB 설정 파일: "
                    + DBConnection.class.getClassLoader().getResource("db.properties"));

            String pwd = props.getProperty("DB_PWD");
            if (pwd == null || pwd.isBlank()) {
                throw new IllegalStateException(
                        "db.properties에 DB_PWD 값이 비어 있습니다. 위 경로의 파일을 확인하세요.");
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("DB 설정을 불러오지 못했습니다: " + e.getMessage(), e);
        }
    }

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                props.getProperty("DB_CONNECTION_URL"),
                props.getProperty("DB_USER"),
                props.getProperty("DB_PWD"));
    }
}
