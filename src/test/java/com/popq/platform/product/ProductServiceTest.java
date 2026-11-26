package com.popq.platform.product;

import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.exception.PopupErrorCode;
import com.popq.platform.popup.exception.PopupException;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.popup.service.PopupAccessService;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// ProductService 단위 테스트 : Repository, 권한 검사, EntityManager는 Mock으로 만들어서 상품 규칙만 확인
@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    ProductRepository productRepo;
    @Mock
    PopupStoreRepository popupRepo;
    @Mock
    PopupAccessService accessService;
    @Mock
    EntityManager em;

    ProductService productService;

    static final String ADMIN = "admin@gmail.com";
    // 상품 판매는 팝업 종료일 다음날부터 가능 : 팝업은 10일 전에 끝났다고 가정
    static final LocalDate POPUP_END = LocalDate.now().minusDays(10);

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepo, popupRepo, accessService, em);
    }

    private PopupStore createPopup(String opStatus) {
        return PopupStore.builder()
                .popId(1L)
                .title("테스트 팝업")
                .startDate(POPUP_END.minusDays(5))
                .endDate(POPUP_END)
                .opStatus(opStatus)
                .build();
    }

    // 판매 기간과 재고만 바꿔서 쓰는 상품
    private Product createProduct(PopupStore popup, LocalDateTime saleStart, LocalDateTime saleEnd, int stock) {
        return Product.builder()
                .prdId(100L)
                .popupStore(popup)
                .productName("테스트 굿즈")
                .price(10000)
                .stock(stock)
                .saleStart(saleStart)
                .saleEnd(saleEnd)
                .maxQty(1)
                .build();
    }

    // 정상 요청 : 판매 기간을 정해서 사용
    private ProductRequest createRequest(LocalDateTime saleStart, LocalDateTime saleEnd) {
        ProductRequest request = new ProductRequest();
        request.setProductName("테스트 굿즈");
        request.setPrice(10000);
        request.setStock(5);
        request.setSaleStart(saleStart);
        request.setSaleEnd(saleEnd);
        return request;
    }

    // 판매 시작 전인 기간 (내일 ~ 10일 뒤)
    private ProductRequest createUpcomingRequest() {
        return createRequest(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(10));
    }

    // 상품 예외가 나는지, 어떤 에러 코드인지 확인
    private void assertErrorCode(ProductErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        ProductException e = assertThrows(ProductException.class, action);
        assertEquals(expected, e.getErrorCode());
    }

    // 상품 등록

    @Test
    @DisplayName("등록 성공 : 구매 제한 수량을 비우면 1개, 판매 시작 전이면 UPCOMING")
    void insertProduct_success() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("OPEN"));
        when(productRepo.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.insertProduct(1L, createUpcomingRequest(), ADMIN);

        assertEquals(5, response.getStock());
        assertEquals(1, response.getMaxQty());
        assertEquals("UPCOMING", response.getSaleStatus());
    }

    @Test
    @DisplayName("등록 실패 : 운영 취소된 팝업이면 POPUP_CANCELLED_MODIFICATION_DENIED")
    void insertProduct_cancelledPopup() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("CANCELLED"));

        PopupException e = assertThrows(PopupException.class,
                () -> productService.insertProduct(1L, createUpcomingRequest(), ADMIN));
        assertEquals(PopupErrorCode.POPUP_CANCELLED_MODIFICATION_DENIED, e.getErrorCode());
        verify(productRepo, never()).save(any());
    }

    @Test
    @DisplayName("등록 실패 : 상품명이 비어 있으면 PRODUCT_NAME_REQUIRED")
    void insertProduct_nameRequired() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("OPEN"));
        ProductRequest request = createUpcomingRequest();
        request.setProductName(" ");

        assertErrorCode(ProductErrorCode.PRODUCT_NAME_REQUIRED, () -> productService.insertProduct(1L, request, ADMIN));
    }

    @Test
    @DisplayName("등록 실패 : 가격이 음수면 INVALID_PRICE")
    void insertProduct_invalidPrice() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("OPEN"));
        ProductRequest request = createUpcomingRequest();
        request.setPrice(-1);

        assertErrorCode(ProductErrorCode.INVALID_PRICE, () -> productService.insertProduct(1L, request, ADMIN));
    }

    @Test
    @DisplayName("등록 실패 : 구매 제한 수량이 0 이하면 INVALID_MAX_QTY")
    void insertProduct_invalidMaxQty() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("OPEN"));
        ProductRequest request = createUpcomingRequest();
        request.setMaxQty(0);

        assertErrorCode(ProductErrorCode.INVALID_MAX_QTY, () -> productService.insertProduct(1L, request, ADMIN));
    }

    @Test
    @DisplayName("등록 실패 : 판매 시작일이 팝업 종료일 당일 이전이면 INVALID_SALE_START")
    void insertProduct_invalidSaleStart() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("OPEN"));
        ProductRequest request = createRequest(POPUP_END.atTime(10, 0), POPUP_END.plusDays(5).atStartOfDay()); // 종료일 당일 시작

        assertErrorCode(ProductErrorCode.INVALID_SALE_START, () -> productService.insertProduct(1L, request, ADMIN));
    }

    @Test
    @DisplayName("등록 실패 : 판매 종료가 판매 시작보다 빠르거나 같으면 INVALID_SALE_PERIOD")
    void insertProduct_invalidSalePeriod() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("OPEN"));
        LocalDateTime start = LocalDateTime.now().plusDays(3);
        ProductRequest request = createRequest(start, start);

        assertErrorCode(ProductErrorCode.INVALID_SALE_PERIOD, () -> productService.insertProduct(1L, request, ADMIN));
    }

    @Test
    @DisplayName("등록 실패 : 재고를 비우면 INVALID_STOCK")
    void insertProduct_invalidStock() {
        when(accessService.checkPopupAccess(1L, ADMIN)).thenReturn(createPopup("OPEN"));
        ProductRequest request = createUpcomingRequest();
        request.setStock(null);

        assertErrorCode(ProductErrorCode.INVALID_STOCK, () -> productService.insertProduct(1L, request, ADMIN));
        verify(productRepo, never()).save(any());
    }

    // 상품 수정

    @Test
    @DisplayName("수정 성공 : 판매 시작 전이면 판매 기간도 자유롭게 변경")
    void updateProduct_beforeSale() {
        Product product = createProduct(createPopup("OPEN"), LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(10), 5);
        when(productRepo.findById(100L)).thenReturn(Optional.of(product));
        ProductRequest request = createRequest(LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(20));
        request.setProductName("바뀐 굿즈");

        ProductResponse response = productService.updateProduct(100L, request, ADMIN);

        assertEquals("바뀐 굿즈", response.getProductName());
        assertEquals(5, response.getStock()); // 재고는 수정 API로 바뀌지 않음
    }

    @Test
    @DisplayName("수정 실패 : 판매 시작 후 판매 시작일을 바꾸면 SALE_START_LOCKED")
    void updateProduct_saleStartLocked() {
        LocalDateTime saleStart = LocalDateTime.now().minusDays(1); // 이미 판매 시작
        Product product = createProduct(createPopup("OPEN"), saleStart, LocalDateTime.now().plusDays(10), 5);
        when(productRepo.findById(100L)).thenReturn(Optional.of(product));
        ProductRequest request = createRequest(saleStart.minusDays(1), LocalDateTime.now().plusDays(10));

        assertErrorCode(ProductErrorCode.SALE_START_LOCKED, () -> productService.updateProduct(100L, request, ADMIN));
    }

    @Test
    @DisplayName("수정 실패 : 판매 시작 후 판매 종료일을 지금 이전으로 바꾸면 INVALID_SALE_END")
    void updateProduct_invalidSaleEnd() {
        LocalDateTime saleStart = LocalDateTime.now().minusDays(3);
        Product product = createProduct(createPopup("OPEN"), saleStart, LocalDateTime.now().plusDays(10), 5);
        when(productRepo.findById(100L)).thenReturn(Optional.of(product));
        ProductRequest request = createRequest(saleStart, LocalDateTime.now().minusDays(1));

        assertErrorCode(ProductErrorCode.INVALID_SALE_END, () -> productService.updateProduct(100L, request, ADMIN));
    }

    @Test
    @DisplayName("수정 실패 : 없는 상품이면 PRODUCT_NOT_FOUND")
    void updateProduct_notFound() {
        when(productRepo.findById(999L)).thenReturn(Optional.empty());

        assertErrorCode(ProductErrorCode.PRODUCT_NOT_FOUND, () -> productService.updateProduct(999L, createUpcomingRequest(), ADMIN));
    }

    // 재고 조정

    @Test
    @DisplayName("재고 조정 실패 : 조정 수량이 0이면 INVALID_STOCK_QUANTITY")
    void adjustStock_zero() {
        assertErrorCode(ProductErrorCode.INVALID_STOCK_QUANTITY, () -> productService.adjustStock(100L, 0, ADMIN));
        verify(productRepo, never()).findById(anyLong());
    }

    @Test
    @DisplayName("재고 조정 성공 : 양수면 재고 추가 후 DB에서 최신 재고를 다시 읽음")
    void adjustStock_increase() {
        Product product = createProduct(createPopup("OPEN"), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10), 5);
        when(productRepo.findById(100L)).thenReturn(Optional.of(product));
        when(productRepo.increaseStock(100L, 3)).thenReturn(1);

        productService.adjustStock(100L, 3, ADMIN);

        verify(productRepo).increaseStock(100L, 3);
        verify(em).refresh(product);
    }

    @Test
    @DisplayName("재고 조정 성공 : 판매 시작 전에는 차감 가능")
    void adjustStock_decreaseBeforeSale() {
        Product product = createProduct(createPopup("OPEN"), LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(10), 5);
        when(productRepo.findById(100L)).thenReturn(Optional.of(product));
        when(productRepo.decreaseStock(100L, 2)).thenReturn(1);

        productService.adjustStock(100L, -2, ADMIN);

        verify(productRepo).decreaseStock(100L, 2); // 음수 -2를 차감 수량 2로 바꿔서 호출
    }

    @Test
    @DisplayName("재고 조정 실패 : 판매 시작 후에는 차감 불가 STOCK_DECREASE_NOT_ALLOWED")
    void adjustStock_decreaseAfterSale() {
        Product product = createProduct(createPopup("OPEN"), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10), 5);
        when(productRepo.findById(100L)).thenReturn(Optional.of(product));

        assertErrorCode(ProductErrorCode.STOCK_DECREASE_NOT_ALLOWED, () -> productService.adjustStock(100L, -1, ADMIN));
        verify(productRepo, never()).decreaseStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("재고 조정 실패 : 재고보다 많이 차감하면(UPDATE 0건) STOCK_SHORTAGE")
    void adjustStock_shortage() {
        Product product = createProduct(createPopup("OPEN"), LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(10), 2);
        when(productRepo.findById(100L)).thenReturn(Optional.of(product));
        when(productRepo.decreaseStock(100L, 5)).thenReturn(0); // 조건부 UPDATE가 0건

        assertErrorCode(ProductErrorCode.STOCK_SHORTAGE, () -> productService.adjustStock(100L, -5, ADMIN));
        verify(em, never()).refresh(any());
    }

    @Test
    @DisplayName("재고 조정 실패 : 담당이 아닌 매니저면 POPUP_ACCESS_DENIED, 재고 변경 안 됨")
    void adjustStock_accessDenied() {
        PopupStore popup = createPopup("OPEN");
        Product product = createProduct(popup, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(10), 5);
        when(productRepo.findById(100L)).thenReturn(Optional.of(product));
        doThrow(new PopupException(PopupErrorCode.POPUP_ACCESS_DENIED)).when(accessService).checkAccess(eq(popup), any());

        PopupException e = assertThrows(PopupException.class, () -> productService.adjustStock(100L, 3, "other@gmail.com"));
        assertEquals(PopupErrorCode.POPUP_ACCESS_DENIED, e.getErrorCode());
        verify(productRepo, never()).increaseStock(anyLong(), anyInt());
    }


    // 상품 삭제

    @Test
    @DisplayName("삭제 실패 : 주문 내역이 있어서 FK 에러가 나면 PRODUCT_HAS_ORDERS")
    void deleteProduct_hasOrders() {
        Product product = createProduct(createPopup("OPEN"), LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(10), 5);
        when(productRepo.findById(100L)).thenReturn(Optional.of(product));
        doThrow(new DataIntegrityViolationException("FK")).when(productRepo).flush();

        assertErrorCode(ProductErrorCode.PRODUCT_HAS_ORDERS, () -> productService.deleteProduct(100L, ADMIN));
    }

    @Test
    @DisplayName("삭제 성공 : 주문 내역이 없으면 삭제 후 바로 flush")
    void deleteProduct_success() {
        Product product = createProduct(createPopup("OPEN"), LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(10), 5);
        when(productRepo.findById(100L)).thenReturn(Optional.of(product));

        productService.deleteProduct(100L, ADMIN);

        verify(productRepo).delete(product);
        verify(productRepo).flush();
    }

    // 판매 상태

    @Test
    @DisplayName("판매 상태 : 판매 기간 중 재고가 있으면 ON_SALE, 0이면 SOLD_OUT")
    void saleStatus_onSaleAndSoldOut() {
        PopupStore popup = createPopup("OPEN");
        Product onSale = createProduct(popup, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10), 5);
        Product soldOut = createProduct(popup, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10), 0);

        assertEquals("ON_SALE", ProductResponse.toDto(onSale).getSaleStatus());
        assertEquals("SOLD_OUT", ProductResponse.toDto(soldOut).getSaleStatus());
    }

    @Test
    @DisplayName("판매 상태 : 판매 종료일이 지나면 ENDED")
    void saleStatus_ended() {
        Product product = createProduct(createPopup("OPEN"), LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(1), 5);

        assertEquals("ENDED", ProductResponse.toDto(product).getSaleStatus());
    }

    @Test
    @DisplayName("판매 상태 : 운영 취소된 팝업의 상품은 판매 기간·재고와 상관없이 ENDED")
    void saleStatus_cancelledPopup() {
        Product product = createProduct(createPopup("CANCELLED"), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10), 5);
        when(productRepo.findById(100L)).thenReturn(Optional.of(product));

        assertEquals("ENDED", productService.detailProduct(100L).getSaleStatus());
    }

    // 관리자 상품 목록

    @Test
    @DisplayName("관리자 목록 : 팝업을 지정하면 그 팝업 권한을 확인하고, 검색어 앞뒤 공백을 지워서 조회")
    void listAdminProduct_filter() {
        when(accessService.getManagerIdForList(ADMIN)).thenReturn(null); // ADMIN은 전체
        when(productRepo.findAdminList(null, 1L, "키링")).thenReturn(List.of());

        productService.listAdminProduct(ADMIN, 1L, "  키링 ");

        verify(accessService).checkPopupAccess(1L, ADMIN);
        verify(productRepo).findAdminList(null, 1L, "키링");
    }
}
