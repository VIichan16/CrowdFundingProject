package org.example.view;

import org.example.dto.LoginUser;
import org.example.common.AppSupport;
import org.example.service.MakerService;

public class MakerView {
    public void start(LoginUser user) throws Exception {
        while (true) {
            System.out.println("\n========== 메이커 메뉴 ==========");
            System.out.println("1. 내 프로젝트 펀딩 현황");
            System.out.println("2. 프로젝트 등록");
            System.out.println("3. 리워드 등록");
            System.out.println("0. 로그아웃");
            String menu = AppSupport.readLine("선택: ");
            if ("0".equals(menu)) return;
            try {
                switch (menu) {
                    case "1" -> MakerService.test01ViewFundingStatus();
                    case "2" -> MakerService.test02RegisterProject();
                    case "3" -> MakerService.test03RegisterReward();
                    default -> System.out.println("올바른 메뉴를 선택하세요.");
                }
            } catch (Exception e) { System.out.println("처리 실패: " + e.getMessage()); }
        }
    }
}
