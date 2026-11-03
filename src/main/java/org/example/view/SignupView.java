package org.example.view;

import org.example.common.AppSupport;
import org.example.service.AuthService;

public class SignupView {
    public void start() {
        System.out.println("\n========== 회원가입 ==========");

        try {
            String loginId = AppSupport.readLine("아이디 (영문/숫자/밑줄, 4~20자): ");
            String password = AppSupport.readLine("비밀번호 (8~72자): ");
            String passwordCheck = AppSupport.readLine("비밀번호 확인: ");

            if (!password.equals(passwordCheck)) {
                System.out.println("비밀번호가 일치하지 않습니다.");
                return;
            }

            String name = AppSupport.readLine("이름: ");
            String phone = AppSupport.readLine("전화번호 (예: 010-1234-5678): ");

            System.out.println("회원 유형을 선택하세요.");
            System.out.println("1. 창작자 (MAKER)");
            System.out.println("2. 후원자 (SUPPORTER)");
            String choice = AppSupport.readLine("선택: ");

            String role;
            if ("1".equals(choice)) {
                role = "MAKER";
            } else if ("2".equals(choice)) {
                role = "SUPPORTER";
            } else {
                System.out.println("올바른 회원 유형을 선택하세요.");
                return;
            }

            AuthService.register(loginId, password, name, phone, role);
            System.out.println("회원가입이 완료되었습니다. 로그인해 주세요.");
        } catch (IllegalArgumentException e) {
            System.out.println("회원가입 실패: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("회원가입 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}
