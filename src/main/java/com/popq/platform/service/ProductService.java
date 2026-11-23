package com.popq.platform.service;

import com.popq.platform.dto.AdminProductResponse;
import com.popq.platform.dto.ProductRequest;
import com.popq.platform.dto.ProductResponse;
import com.popq.platform.entity.PopupStore;
import com.popq.platform.entity.Product;
import com.popq.platform.exception.PopupErrorCode;
import com.popq.platform.exception.PopupException;
import com.popq.platform.exception.ProductErrorCode;
import com.popq.platform.exception.ProductException;
import com.popq.platform.repository.PopupStoreRepository;
import com.popq.platform.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true) // 기본 읽기 전용, 쓰기 메소드에만 @Transactional
public class ProductService {
    ProductRepository productRepo;
    PopupStoreRepository popupRepo;
    PopupAccessService accessService;
    EntityManager em; // 재고 조정 후 최신 재고를 다시 읽을 때 사용

    @Autowired
    public ProductService(ProductRepository productRepo, PopupStoreRepository popupRepo, PopupAccessService accessService, EntityManager em) {
        this.productRepo = productRepo;
        this.popupRepo = popupRepo;
        this.accessService = accessService;
        this.em = em;
    }

    // 팝업의 상품 목록
    public List<ProductResponse> listProduct(Long popId) {
        findPopup(popId); // 없는 팝업이면 404
        return toDtoList(productRepo.findByPopId(popId));
    }

    // 상품 상세 조회
    public ProductResponse detailProduct(Long prdId) {
        return ProductResponse.toDto(findProduct(prdId));
    }

    // 상품 ID 목록으로 여러 상품 조회 (주문/장바구니 연동용) : 존재하는 상품만 반환
    public List<ProductResponse> listProductByIds(List<Long> prdIds) {
        return toDtoList(productRepo.findAllById(prdIds));
    }

    // 관리자 상품 목록 : ADMIN은 전체, MANAGER는 본인 팝업의 상품만 / popId(팝업), keyword(상품명) 필터
    public List<AdminProductResponse> listAdminProduct(String username, Long popId, String keyword) {
        Long managerId = accessService.getManagerIdForList(username); // ADMIN null, MANAGER 본인 id, 그 외 403
        if (popId != null) {
            accessService.checkPopupAccess(popId, username); // 없는 팝업 404, 담당이 아니면 403
        }
        if (keyword == null || keyword.isBlank()) { // 빈 검색어는 검색 안 함
            keyword = null;
        } else {
            keyword = keyword.trim();
        }
        List<AdminProductResponse> dtoList = new ArrayList<AdminProductResponse>();
        for (Product e : productRepo.findAdminList(managerId, popId, keyword)) {
            dtoList.add(AdminProductResponse.toDto(e));
        }
        return dtoList;
    }

    // 상품 등록 : 등록된 상품 정보 반환
    @Transactional
    public ProductResponse insertProduct(Long popId, ProductRequest request, String username) {
        PopupStore popup = accessService.checkPopupAccess(popId, username); // 없으면 404, 담당이 아니면 403

        popup.validateNotCancelled(); // 취소된 팝업의 하위 굿즈는 등록 불가

        checkProduct(popup, request);
        // DB에 DEFAULT 0이 있어도 JPA는 null을 그대로 INSERT 하므로 여기서 확인
        if (request.getStock() == null || request.getStock() < 0) {
            throw new ProductException(ProductErrorCode.INVALID_STOCK);
        }
        Product saved = productRepo.save(Product.toEntity(null, popup, request, request.getStock()));
        return ProductResponse.toDto(saved); // prdId 대신 상품 정보 전체 반환
    }

    // 상품 수정 : 팝업은 바꿀 수 없고, 재고는 수정되지 않음 (재고 조정 API 사용) / 수정된 상품 정보 반환
    @Transactional
    public ProductResponse updateProduct(Long prdId, ProductRequest request, String username) {
        Product product = findProduct(prdId);
        PopupStore popup = product.getPopupStore();
        accessService.checkAccess(popup, accessService.getLoginUser(username)); // 담당 팝업이 아니면 403

        popup.validateNotCancelled(); // 취소된 팝업의 하위 굿즈는 수정 불가

        checkProduct(popup, request);
        checkSalePeriodChange(product, request);
        product.update(request); // 변경 감지 : save() 없이 트랜잭션이 끝날 때 자동으로 UPDATE
        return ProductResponse.toDto(product);
    }

    // 재고 조정 : quantity 양수면 추가, 음수면 차감 / 조정 후 상품 정보 반환
    @Transactional
    public ProductResponse adjustStock(Long prdId, Integer quantity, String username) {
        if (quantity == null || quantity == 0) {
            throw new ProductException(ProductErrorCode.INVALID_STOCK_QUANTITY);
        }
        Product product = findProduct(prdId); // 없는 상품이면 404
        PopupStore popup = product.getPopupStore();
        accessService.checkAccess(product.getPopupStore(), accessService.getLoginUser(username)); // 담당 팝업이 아니면 403

        popup.validateNotCancelled(); // 취소된 팝업의 하위 굿즈는 재고 조정 불가

        // 판매가 시작된 상품은 재고 추가만 가능
        if (quantity < 0 && !LocalDateTime.now().isBefore(product.getSaleStart())) {
            throw new ProductException(ProductErrorCode.STOCK_DECREASE_NOT_ALLOWED);
        }

        int result;
        if (quantity > 0) {
            result = productRepo.increaseStock(prdId, quantity);
        } else {
            result = productRepo.decreaseStock(prdId, -quantity); // 재고가 충분할 때만 차감
        }
        if (result == 0) {
            throw new ProductException(ProductErrorCode.STOCK_SHORTAGE); // 상품은 위에서 이미 확인함
        }
        em.refresh(product); // UPDATE 쿼리는 이미 조회한 객체를 바꾸지 않으므로 DB에서 바뀐 재고를 다시 읽어옴
        return ProductResponse.toDto(product);
    }

    // 상품 삭제 : 주문 내역이 있으면 삭제 불가 (order_item FK)
    @Transactional
    public void deleteProduct(Long prdId, String username) {
        Product product = findProduct(prdId);
        PopupStore popup = product.getPopupStore();
        accessService.checkAccess(product.getPopupStore(), accessService.getLoginUser(username)); // 담당 팝업이 아니면 403

        popup.validateNotCancelled(); // 취소된 팝업의 하위 굿즈는 삭제 불가

        try {
            productRepo.delete(product);
            productRepo.flush(); // 여기서 바로 DELETE 실행해야 FK 에러를 잡을 수 있음
        } catch (DataIntegrityViolationException e) {
            throw new ProductException(ProductErrorCode.PRODUCT_HAS_ORDERS);
        }
    }

    //내부 메소드

    private PopupStore findPopup(Long popId) {
        return popupRepo.findById(popId)
                .orElseThrow(() -> new PopupException(PopupErrorCode.POPUP_NOT_FOUND));
    }

    private Product findProduct(Long prdId) {
        return productRepo.findById(prdId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    private List<ProductResponse> toDtoList(List<Product> entityList) {
        List<ProductResponse> dtoList = new ArrayList<ProductResponse>();
        for (Product e : entityList) {
            dtoList.add(ProductResponse.toDto(e));
        }
        return dtoList;
    }

    // 필수값, 가격, 구매 제한, 판매기간 (팝업 종료일 다음날부터, 시작 < 종료)
    private void checkProduct(PopupStore popup, ProductRequest request) {
        if (request.getProductName() == null || request.getProductName().isBlank()) {
            throw new ProductException(ProductErrorCode.PRODUCT_NAME_REQUIRED);
        }
        if (request.getPrice() == null || request.getPrice() < 0) {
            throw new ProductException(ProductErrorCode.INVALID_PRICE);
        }
        if (request.getMaxQty() != null && request.getMaxQty() <= 0) {
            throw new ProductException(ProductErrorCode.INVALID_MAX_QTY);
        }
        if (request.getSaleStart() == null || request.getSaleEnd() == null) {
            throw new ProductException(ProductErrorCode.SALE_PERIOD_REQUIRED);
        }
        if (!request.getSaleStart().toLocalDate().isAfter(popup.getEndDate())) {
            throw new ProductException(ProductErrorCode.INVALID_SALE_START);
        }
        if (!request.getSaleEnd().isAfter(request.getSaleStart())) {
            throw new ProductException(ProductErrorCode.INVALID_SALE_PERIOD);
        }
    }

    // 판매가 시작된 상품 : 판매 시작일시는 변경 불가, 판매 종료일시는 지금 이후로만 변경 가능
    private void checkSalePeriodChange(Product product, ProductRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(product.getSaleStart())) {
            return; // 판매 시작 전에는 자유롭게 수정
        }
        if (!request.getSaleStart().isEqual(product.getSaleStart())) {
            throw new ProductException(ProductErrorCode.SALE_START_LOCKED);
        }
        if (!request.getSaleEnd().isEqual(product.getSaleEnd()) && !request.getSaleEnd().isAfter(now)) {
            throw new ProductException(ProductErrorCode.INVALID_SALE_END);
        }
    }
}