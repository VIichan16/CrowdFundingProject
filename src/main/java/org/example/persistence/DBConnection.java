package org.example.persistence;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class DBConnection {
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = DBConnection.class.getClassLoader()
                .getResourceAsStream("db.properties")) {
            if (input == null) {
                throw new IllegalStateException(
                        "db.properties 파일을 찾을 수 없습니다.");
            }
            properties.load(input);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private DBConnection() {
    }

    public static Connection getConnection() throws Exception {
        String url = properties.getProperty("db.url");
        String username = properties.getProperty("db.username");
        String password = properties.getProperty("db.password");

        if (url == null || username == null || password == null
                || password.equals("여기에_MySQL_비밀번호_입력")) {
            throw new IllegalStateException(
                    "db.properties의 접속 정보를 설정하세요.");
        }

        return DriverManager.getConnection(url, username, password);
    }
}