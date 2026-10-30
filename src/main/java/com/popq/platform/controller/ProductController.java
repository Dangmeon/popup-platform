package com.popq.platform.controller;

import com.popq.platform.dto.ProductRequest;
import com.popq.platform.dto.ProductResponse;
import com.popq.platform.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin("*")
@RestController
public class ProductController {
    ProductService service;

    @Autowired
    public ProductController(ProductService service) {
        this.service = service;
    }

    // 팝업의 상품 목록 조회
    @GetMapping("/api/popups/{popId}/products")
    public List<ProductResponse> listProduct(@PathVariable("popId") Long popId) {
        return service.listProduct(popId);
    }

    // 상품 상세 조회
    @GetMapping("/api/products/{prdId}")
    public ProductResponse detailProduct(@PathVariable("prdId") Long prdId) {
        return service.detailProduct(prdId);
    }

    // 아래 등록/수정/재고 조정/삭제는 /api/admin/** 이라 SecurityConfig에서 ADMIN만 호출 가능

    // 상품 등록
    @PostMapping("/api/admin/popups/{popId}/products")
    public ProductResponse insertProduct(@PathVariable("popId") Long popId, @RequestBody ProductRequest request) {
        return service.insertProduct(popId, request); // 등록된 상품 정보 반환
    }

    // 상품 수정 (재고는 수정되지 않음, 판매 시작 후에는 판매 시작일시 변경 불가)
    @PutMapping("/api/admin/products/{prdId}")
    public void updateProduct(@PathVariable("prdId") Long prdId, @RequestBody ProductRequest request) {
        service.updateProduct(prdId, request);
    }

    // 재고 조정
    // /api/admin/products/1/stock?quantity=10 추가 / ?quantity=-5 차감 (판매 시작 후에는 추가만 가능)
    @PutMapping("/api/admin/products/{prdId}/stock")
    public ProductResponse adjustStock(@PathVariable("prdId") Long prdId, @RequestParam("quantity") Integer quantity) {
        return service.adjustStock(prdId, quantity); // 조정 후 상품 정보 반환
    }

    // 상품 삭제
    @DeleteMapping("/api/admin/products/{prdId}")
    public void deleteProduct(@PathVariable("prdId") Long prdId) {
        service.deleteProduct(prdId);
    }
}
