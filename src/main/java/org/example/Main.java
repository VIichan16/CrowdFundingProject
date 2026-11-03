package org.example;

import org.example.init.DatabaseInitializer;
import org.example.view.LoginView;

public class Main {
    public static void main(String[] args) {
        try {
            DatabaseInitializer.initDB();
            new LoginView().start();
        } catch (Exception e) {
            System.out.println("프로그램 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
