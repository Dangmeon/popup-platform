package com.popq.platform.entity;

import com.popq.platform.dto.ProductRequest;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @SequenceGenerator(name = "product_seq_gen", sequenceName = "product_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq_gen")
    @Column(name = "prd_id")
    private Long prdId;

    // 어느 팝업의 상품인지 (product.pop_id -> popup_store.pop_id FK)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pop_id")
    private PopupStore popupStore;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "price")
    private Integer price;

    // 재고는 주문 차감/복구(조건부 UPDATE)로만 변경 -> 상품 수정(save)으로 덮어쓰지 않음
    @Column(name = "stock", updatable = false)
    private Integer stock;

    @Column(name = "sale_start")
    private LocalDateTime saleStart;

    @Column(name = "sale_end")
    private LocalDateTime saleEnd;

    @Column(name = "category")
    private String category;

    @Column(name = "product_img")
    private String productImg;

    @Column(name = "product_desc")
    private String productDesc;

    @Column(name = "max_qty")
    private Integer maxQty;

    // Request -> Entity 변환시 사용
    // 등록할 때만 사용 : prdId = null, stock = 요청값 (수정은 아래 update() 사용)
    public static Product toEntity(Long prdId, PopupStore popupStore, ProductRequest request, Integer stock) {
        return Product.builder()
                .prdId(prdId)
                .popupStore(popupStore)
                .productName(request.getProductName())
                .price(request.getPrice())
                .stock(stock)
                .saleStart(request.getSaleStart())
                .saleEnd(request.getSaleEnd())
                .category(request.getCategory())
                .productImg(request.getProductImg())
                .productDesc(request.getProductDesc())
                .maxQty(request.getMaxQty() != null ? request.getMaxQty() : 1) // 비우면 1개
                .build();
    }

    // 상품 수정 : 수정 가능한 칸만 변경 (팝업, 재고는 바꾸지 않음)
    // DB에서 조회한 객체의 값을 바꾸면 트랜잭션이 끝날 때 JPA가 변경을 감지해서 자동으로 UPDATE
    public void update(ProductRequest request) {
        this.productName = request.getProductName();
        this.price = request.getPrice();
        this.saleStart = request.getSaleStart();
        this.saleEnd = request.getSaleEnd();
        this.category = request.getCategory();
        this.productImg = request.getProductImg();
        this.productDesc = request.getProductDesc();
        if (request.getMaxQty() != null) { // 비우면 기존 값 유지
            this.maxQty = request.getMaxQty();
        }
    }
}
