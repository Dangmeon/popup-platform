package com.popq.platform.service;

import com.popq.platform.dto.PopupSalesResponse;
import com.popq.platform.dto.SalesSummaryResponse;
import com.popq.platform.entity.PopupStore;
import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.repository.OrderItemRepository;
import com.popq.platform.repository.PopupStoreRepository;
import com.popq.platform.repository.SalesSummaryProjection;
import com.popq.platform.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSalesService {

    private final OrderItemRepository orderItemRepository;
    private final PopupStoreRepository popupStoreRepository;
    private final UserRepository userRepository;

    public SalesSummaryResponse getTotalSales(String username) {
        // ADMIN 권한 확인
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if(user.getRole() != Role.ADMIN){
            throw new IllegalArgumentException("전체 매출은 관리자만 조회할 수 있습니다.");
        }

        // 전체 매출 집계
        SalesSummaryProjection result = orderItemRepository.findSalesSummary(OrderStatus.PAID);

        return new SalesSummaryResponse(
                result.getTotalSales(),
                result.getTotalOrderCount(),
                result.getTotalQuantity()
        );
    }

    public PopupSalesResponse getPopupSales(
            String username, Long popId
    ) {
        // 사용자 조회
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 팝업 조회
        PopupStore popupStore = popupStoreRepository.findById(popId)
                .orElseThrow(() -> new RuntimeException("PopupStore not found"));

        // MANAGER면 본인 담당 팝업인지 확인
        if(user.getRole() == Role.MANAGER){
            if(popupStore.getManager() == null || !popupStore.getManager().getId().equals(user.getId())){
                throw new IllegalArgumentException("담당 팝업의 매출만 조회할 수 있습니다.");
            }
        }

        // 팝업별 매출 집계
        SalesSummaryProjection result = orderItemRepository.findSalesSummaryByPopup(popId, OrderStatus.PAID);

        return new PopupSalesResponse(
                popupStore.getPopId(),
                popupStore.getTitle(),
                result.getTotalSales(),
                result.getTotalOrderCount(),
                result.getTotalQuantity()
        );
    }
}
