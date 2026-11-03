package org.example.view;

import org.example.dto.LoginUser;
import org.example.service.AuthService;
import org.example.common.AppSupport;

public class LoginView {
    public void start() throws Exception {
        while (true) {
            System.out.println("\n========== 크라우드펀딩 로그인 ==========");
            System.out.println("1. 로그인");
            System.out.println("2. 회원가입");
            System.out.println("0. 종료");
            String menu = AppSupport.readLine("선택: ");
            if ("0".equals(menu)) {
                System.out.println("프로그램을 종료합니다.");
                return;
            }
            if ("2".equals(menu)) {
                new SignupView().start();
                continue;
            }
            if (!"1".equals(menu)) {
                System.out.println("올바른 메뉴를 선택하세요.");
                continue;
            }
            String id = AppSupport.readLine("아이디: ");
            String pw = AppSupport.readLine("비밀번호: ");
            LoginUser user = AuthService.login(id, pw);
            if (user == null) {
                System.out.println("아이디 또는 비밀번호가 올바르지 않거나 비활성 계정입니다.");
                continue;
            }
            System.out.println("\n로그인 성공: " + user.getName() + " (" + user.getRole() + ")");
            switch (user.getRole()) {
                case "ADMIN" -> new AdminView().start(user);
                case "MAKER" -> new MakerView().start(user);
                case "SUPPORTER" -> new SupporterView().start(user);
                default -> System.out.println("등록되지 않은 사용자 역할입니다: " + user.getRole());
            }
        }
    }
}
