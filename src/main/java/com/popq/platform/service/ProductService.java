package com.popq.platform.service;

import com.popq.platform.dto.ProductRequest;
import com.popq.platform.dto.ProductResponse;
import com.popq.platform.entity.PopupStore;
import com.popq.platform.entity.Product;
import com.popq.platform.repository.PopupStoreRepository;
import com.popq.platform.repository.ProductRepository;
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

    @Autowired
    public ProductService(ProductRepository productRepo, PopupStoreRepository popupRepo) {
        this.productRepo = productRepo;
        this.popupRepo = popupRepo;
    }

    // 팝업의 상품 목록
    public List<ProductResponse> listProduct(Long popId) {
        return toDtoList(productRepo.findByPopupStore_PopIdOrderByPrdIdAsc(popId));
    }

    // 상품 상세 조회
    public ProductResponse detailProduct(Long prdId) {
        return ProductResponse.toDto(findProduct(prdId));
    }

    // 상품 등록 : 생성된 prdId 반환
    @Transactional
    public Long insertProduct(ProductRequest request) {
        PopupStore popup = findPopup(request.getPopId());
        checkProduct(popup, request);
        // DB에 DEFAULT 0이 있어도 JPA는 null을 그대로 INSERT 하므로 여기서 확인
        if (request.getStock() == null || request.getStock() < 0) {
            throw new IllegalArgumentException("재고는 0개 이상 입력해주세요.");
        }
        Product saved = productRepo.save(Product.toEntity(null, popup, request, request.getStock()));
        return saved.getPrdId();
    }

    // 상품 수정 : 팝업은 바꿀 수 없고, 재고는 수정되지 않음 (재고 조정 API 사용)
    @Transactional
    public void updateProduct(Long prdId, ProductRequest request) {
        Product product = findProduct(prdId);
        PopupStore popup = product.getPopupStore();
        if (request.getPopId() != null && !request.getPopId().equals(popup.getPopId())) {
            throw new IllegalArgumentException("상품의 팝업은 변경할 수 없습니다.");
        }
        checkProduct(popup, request);
        checkSalePeriodChange(product, request);
        // 구매 제한을 비우면 기존 값 유지 (toEntity에서 1로 초기화되지 않도록)
        if (request.getMaxQty() == null) {
            request.setMaxQty(product.getMaxQty());
        }
        productRepo.save(Product.toEntity(prdId, popup, request, product.getStock())); // stock은 updatable=false
    }

    // 재고 조정 : quantity 양수면 추가, 음수면 차감 / 조정 후 재고 반환
    @Transactional
    public Integer adjustStock(Long prdId, Integer quantity) {
        if (quantity == null || quantity == 0) {
            throw new IllegalArgumentException("조정 수량을 입력해주세요. 예) 10 (추가) / -5 (차감)");
        }
        // 판매가 시작된 상품은 재고 추가만 가능
        if (quantity < 0) {
            Product product = findProduct(prdId);
            if (!LocalDateTime.now().isBefore(product.getSaleStart())) {
                throw new IllegalArgumentException("판매가 시작된 상품은 재고를 추가만 할 수 있습니다.");
            }
        }
        int result;
        if (quantity > 0) {
            result = productRepo.increaseStock(prdId, quantity);
        } else {
            result = productRepo.decreaseStock(prdId, -quantity); // 재고가 충분할 때만 차감
        }
        if (result == 0) {
            findProduct(prdId); // 없는 상품이면 여기서 "존재하지 않는 상품" 예외
            throw new IllegalArgumentException("현재 재고보다 많이 차감할 수 없습니다.");
        }
        return findProduct(prdId).getStock(); // UPDATE 후 영속성 컨텍스트가 비워져서 DB에서 다시 조회됨
    }

    // 상품 삭제 : 주문 내역이 있으면 삭제 불가 (order_item FK)
    @Transactional
    public void deleteProduct(Long prdId) {
        Product product = findProduct(prdId);
        try {
            productRepo.delete(product);
            productRepo.flush(); // 여기서 바로 DELETE 실행해야 FK 에러를 잡을 수 있음
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("주문 내역이 있는 상품은 삭제할 수 없습니다.");
        }
    }

    //내부 메소드

    private PopupStore findPopup(Long popId) {
        if (popId == null) {
            throw new IllegalArgumentException("popId는 필수입니다.");
        }
        return popupRepo.findById(popId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 팝업입니다. popId=" + popId));
    }

    private Product findProduct(Long prdId) {
        return productRepo.findById(prdId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다. prdId=" + prdId));
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
            throw new IllegalArgumentException("상품명은 필수입니다.");
        }
        if (request.getPrice() == null || request.getPrice() < 0) {
            throw new IllegalArgumentException("가격은 0원 이상 입력해주세요.");
        }
        if (request.getMaxQty() != null && request.getMaxQty() <= 0) {
            throw new IllegalArgumentException("1인 구매 제한은 1개 이상이어야 합니다.");
        }
        if (request.getSaleStart() == null || request.getSaleEnd() == null) {
            throw new IllegalArgumentException("판매 시작일시와 종료일시는 필수입니다.");
        }
        if (!request.getSaleStart().toLocalDate().isAfter(popup.getEndDate())) {
            throw new IllegalArgumentException("판매 시작은 팝업 종료일(" + popup.getEndDate() + ") 다음날부터 가능합니다.");
        }
        if (!request.getSaleEnd().isAfter(request.getSaleStart())) {
            throw new IllegalArgumentException("판매 종료는 판매 시작보다 늦어야 합니다.");
        }
    }

    // 판매가 시작된 상품 : 판매 시작일시는 변경 불가, 판매 종료일시는 지금 이후로만 변경 가능
    private void checkSalePeriodChange(Product product, ProductRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(product.getSaleStart())) {
            return; // 판매 시작 전에는 자유롭게 수정
        }
        if (!request.getSaleStart().isEqual(product.getSaleStart())) {
            throw new IllegalArgumentException("판매가 시작된 상품은 판매 시작일시를 변경할 수 없습니다.");
        }
        if (!request.getSaleEnd().isEqual(product.getSaleEnd()) && !request.getSaleEnd().isAfter(now)) {
            throw new IllegalArgumentException("판매 종료일시는 현재 시각 이후로만 변경할 수 있습니다.");
        }
    }
}
