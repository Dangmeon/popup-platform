package com.popq.platform.product;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.product.dto.AdminProductResponse;
import com.popq.platform.product.dto.ProductRequest;
import com.popq.platform.product.dto.ProductResponse;
import com.popq.platform.product.entity.Product;
import com.popq.platform.product.exception.ProductErrorCode;
import com.popq.platform.product.exception.ProductException;
import com.popq.platform.product.repository.ProductRepository;
import com.popq.platform.product.service.ProductService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// 상품 통합 테스트 : 스프링을 실제로 띄우고 로컬 DB에 데이터를 넣어서 서비스 + 쿼리를 함께 확인
// @Transactional : 테스트가 끝나면 넣은 데이터를 모두 롤백 (개발 DB에 남지 않음)
// 실행 전 로컬 Oracle, Redis가 켜져 있어야 함
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ProductIntegrationTest {

    @Autowired
    ProductService productService;
    @Autowired
    ProductRepository productRepo;
    @Autowired
    PopupStoreRepository popupRepo;
    @Autowired
    OrderRepository orderRepo;
    @Autowired
    UserRepository userRepo;
    @Autowired
    EntityManager em;

    User admin;
    User manager;
    User member;
    PopupStore popup;
    String tag; // 이번 테스트에서 만든 상품만 찾기 위한 고유 문자열 (기존 데이터와 섞이지 않게)

    @BeforeEach
    void setUp() {
        tag = "IT" + UUID.randomUUID().toString().substring(0, 8);
        admin = saveUser(Role.ADMIN);
        manager = saveUser(Role.MANAGER);
        member = saveUser(Role.USER);
        popup = savePopup(manager);
    }

    // 테스트 데이터 만들기

    private User saveUser(Role role) {
        String random = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .username("it-" + random + "@gmail.com")
                .password("test")
                .name("통합테스트")
                .phone("010-" + random)
                .role(role)
                .build();
        return userRepo.save(user);
    }

    // 3일 전 ~ 5일 뒤 운영 중인 팝업 (담당 매니저 지정)
    private PopupStore savePopup(User manager) {
        PopupStore popup = PopupStore.builder()
                .title(tag)
                .location("성수")
                .category("ETC")
                .startDate(LocalDate.now().minusDays(3))
                .endDate(LocalDate.now().plusDays(5))
                .reservStart("10:00")
                .reservEnd("20:00")
                .reservPeopleCap(10)
                .build();
        popup.assignManager(manager);
        return popupRepo.save(popup);
    }

    // 판매 시작 전 상품 (팝업 종료 다음날 10:00 ~ 7일 뒤)
    private Product saveProduct(PopupStore popup, String name, int stock) {
        LocalDateTime saleStart = popup.getEndDate().plusDays(1).atTime(10, 0);
        Product product = Product.builder()
                .popupStore(popup)
                .productName(name)
                .price(10000)
                .stock(stock)
                .saleStart(saleStart)
                .saleEnd(saleStart.plusDays(7))
                .maxQty(1)
                .build();
        return productRepo.save(product);
    }

    // 상품 1개가 들어간 주문 (order_item이 상품을 참조)
    private Order saveOrder(Product product) {
        Order order = Order.createOrder(member, 10000, LocalDateTime.now().plusMinutes(10),
                "통합테스트", "010-0000-0000", "서울", "101호", null, null);
        order.addOrderItem(OrderItem.createOrderItem(product, 1, 10000, 10000));
        return orderRepo.save(order); // 주문 상품도 같이 저장 (cascade)
    }

    private ProductRequest makeRequest(String name, Integer stock, Integer maxQty) {
        LocalDateTime saleStart = popup.getEndDate().plusDays(1).atTime(10, 0);
        ProductRequest request = new ProductRequest();
        request.setProductName(name);
        request.setPrice(15000);
        request.setStock(stock);
        request.setSaleStart(saleStart);
        request.setSaleEnd(saleStart.plusDays(7));
        request.setMaxQty(maxQty);
        return request;
    }

    // 지금까지 바꾼 내용을 DB에 반영하고, 1차 캐시를 비워서 다음 조회는 DB에서 새로 읽게 함
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    // 등록 / 수정

    @Test
    @DisplayName("등록 : 요청한 재고로 저장되고, 구매 제한을 비우면 1개로 저장")
    void insertProduct_savesStockAndDefaultMaxQty() {
        ProductResponse response = productService.insertProduct(popup.getPopId(), makeRequest(tag + " 머그컵", 50, null), manager.getUsername());
        flushAndClear();

        Product saved = productRepo.findById(response.getPrdId()).get();
        assertEquals(50, saved.getStock());
        assertEquals(1, saved.getMaxQty());
        assertEquals(popup.getPopId(), saved.getPopupStore().getPopId());
    }

    @Test
    @DisplayName("수정 : 상품명은 바뀌고, 요청에 재고를 넣어도 DB 재고는 그대로 (stock updatable = false)")
    void updateProduct_doesNotChangeStock() {
        Product product = saveProduct(popup, tag + " 머그컵", 50);
        flushAndClear();

        productService.updateProduct(product.getPrdId(), makeRequest(tag + " 텀블러", 999, 2), manager.getUsername());
        flushAndClear();

        Product saved = productRepo.findById(product.getPrdId()).get();
        assertEquals(tag + " 텀블러", saved.getProductName());
        assertEquals(2, saved.getMaxQty());
        assertEquals(50, saved.getStock());
    }

    // 재고 조정

    @Test
    @DisplayName("재고 추가 : DB 재고가 늘고, 응답에도 바뀐 재고가 나옴 (em.refresh)")
    void adjustStock_increase() {
        Product product = saveProduct(popup, tag + " 머그컵", 50);
        flushAndClear();

        ProductResponse response = productService.adjustStock(product.getPrdId(), 10, manager.getUsername());
        assertEquals(60, response.getStock());
        flushAndClear();

        assertEquals(60, productRepo.findById(product.getPrdId()).get().getStock());
    }

    @Test
    @DisplayName("재고 차감 : 재고보다 많이 빼면 409이고 재고 그대로, 재고 안에서는 차감됨")
    void adjustStock_decreaseConditionalUpdate() {
        Product product = saveProduct(popup, tag + " 머그컵", 5);
        flushAndClear();

        ProductException e = assertThrows(ProductException.class,
                () -> productService.adjustStock(product.getPrdId(), -10, manager.getUsername()));
        assertEquals(ProductErrorCode.STOCK_SHORTAGE, e.getErrorCode());
        flushAndClear();
        assertEquals(5, productRepo.findById(product.getPrdId()).get().getStock());

        ProductResponse response = productService.adjustStock(product.getPrdId(), -3, manager.getUsername());
        assertEquals(2, response.getStock());
        flushAndClear();
        assertEquals(2, productRepo.findById(product.getPrdId()).get().getStock());
    }

    // 삭제

    @Test
    @DisplayName("삭제 : 주문 내역이 없으면 DB에서 삭제")
    void deleteProduct_noOrders() {
        Product product = saveProduct(popup, tag + " 머그컵", 50);
        flushAndClear();

        productService.deleteProduct(product.getPrdId(), manager.getUsername());
        flushAndClear();

        assertTrue(productRepo.findById(product.getPrdId()).isEmpty());
    }

    @Test
    @DisplayName("삭제 실패 : 주문 내역이 있으면 FK 에러를 409로 바꿔서 반환, 상품은 남아 있음")
    void deleteProduct_blockedByOrderItem() {
        Product product = saveProduct(popup, tag + " 머그컵", 50);
        saveOrder(product);
        flushAndClear();

        ProductException e = assertThrows(ProductException.class,
                () -> productService.deleteProduct(product.getPrdId(), manager.getUsername()));
        assertEquals(ProductErrorCode.PRODUCT_HAS_ORDERS, e.getErrorCode());

        em.clear(); // 실패한 DELETE가 다시 실행되지 않도록 flush 없이 1차 캐시만 비움
        assertTrue(productRepo.findById(product.getPrdId()).isPresent());
    }

    // 관리자 상품 목록

    @Test
    @DisplayName("관리자 목록 : ADMIN은 전체, MANAGER는 본인 팝업 상품만, 검색어로 상품명 필터")
    void listAdminProduct_byRoleAndKeyword() {
        User otherManager = saveUser(Role.MANAGER);
        PopupStore otherPopup = savePopup(otherManager);
        saveProduct(popup, tag + " 머그컵", 50);
        saveProduct(popup, tag + " 키링", 50);
        saveProduct(otherPopup, tag + " 티셔츠", 50);
        flushAndClear();

        List<AdminProductResponse> adminResult = productService.listAdminProduct(admin.getUsername(), null, tag);
        assertEquals(3, adminResult.size());

        List<AdminProductResponse> managerResult = productService.listAdminProduct(manager.getUsername(), null, tag);
        assertEquals(2, managerResult.size());
        for (AdminProductResponse dto : managerResult) {
            assertEquals(popup.getPopId(), dto.getPopId());
        }

        List<AdminProductResponse> keywordResult = productService.listAdminProduct(admin.getUsername(), null, tag + " 키링");
        assertEquals(1, keywordResult.size());
        assertEquals(tag + " 키링", keywordResult.get(0).getProductName());
    }
}
