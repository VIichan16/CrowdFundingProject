package org.example.view;

import org.example.dto.LoginUser;
import org.example.common.AppSupport;
import org.example.service.AdminService;

public class AdminView {
    public void start(LoginUser user) throws Exception {
        while (true) {
            System.out.println("\n========== 관리자 메뉴 ==========");
            System.out.println("1. 프로젝트 승인 처리");
            System.out.println("0. 로그아웃");
            String menu = AppSupport.readLine("선택: ");
            if ("0".equals(menu)) return;
            try {
                if ("1".equals(menu)) AdminService.test04ApproveProject();
                else System.out.println("올바른 메뉴를 선택하세요.");
            } catch (Exception e) { System.out.println("처리 실패: " + e.getMessage()); }
        }
    }
}
