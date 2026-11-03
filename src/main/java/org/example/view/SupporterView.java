package org.example.view;

import org.example.dto.LoginUser;
import org.example.common.AppSupport;
import org.example.service.ProjectService;
import org.example.service.FundingService;
import org.example.service.ReviewService;
import org.example.service.UserService;

public class SupporterView {
    public void start(LoginUser user) throws Exception {
        while (true) {
            System.out.println("\n========== 서포터 메뉴 ==========");
            System.out.println("1. 프로젝트 목록");
            System.out.println("2. 프로젝트 검색 및 필터");
            System.out.println("3. 리워드 후원 신청");
            System.out.println("4. 리뷰 등록/수정");
            System.out.println("5. 내 정보 수정");
            System.out.println("6. 재고 동시성 테스트");
            System.out.println("0. 로그아웃");
            String menu = AppSupport.readLine("선택: ");
            if ("0".equals(menu)) return;
            try {
                switch (menu) {
                    case "1" -> ProjectService.test06ViewProjects();
                    case "2" -> ProjectService.test07FilterProjects();
                    case "3" -> FundingService.test08ApplyReward();
                    case "4" -> ReviewService.test09RegisterReview();
                    case "5" -> UserService.test05UpdateProfile();
                    case "6" -> FundingService.test10ConcurrencyControl();
                    default -> System.out.println("올바른 메뉴를 선택하세요.");
                }
            } catch (Exception e) { System.out.println("처리 실패: " + e.getMessage()); }
        }
    }
}
