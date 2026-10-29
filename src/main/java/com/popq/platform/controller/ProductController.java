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
    // /product/list/1 : 1번 팝업의 상품 전체 (등록순)
    @GetMapping("/product/list/{popId}")
    public List<ProductResponse> listProduct(@PathVariable("popId") Long popId) {
        return service.listProduct(popId);
    }

    // 상품 상세 조회
    @GetMapping("/product/detail/{prdId}")
    public ProductResponse detailProduct(@PathVariable("prdId") Long prdId) {
        return service.detailProduct(prdId);
    }

    // [회원 연동] 아래 등록/수정/재고 조정/삭제는 관리자(ADMIN)만 호출 가능하도록 권한 체크 필요
    //  - Security/ADMIN 권한 작업 후 적용

    // 상품 등록
    @PostMapping("/product/insert")
    public Long insertProduct(@RequestBody ProductRequest request) {
        return service.insertProduct(request); // 생성된 prdId 반환
    }

    // 상품 수정 (재고는 수정되지 않음, 판매 시작 후에는 판매 시작일시 변경 불가)
    @PutMapping("/product/update/{prdId}")
    public void updateProduct(@PathVariable("prdId") Long prdId, @RequestBody ProductRequest request) {
        service.updateProduct(prdId, request);
    }

    // 재고 조정 : body 대신 URL 파라미터로 받음
    // /product/stock/1?quantity=10 추가 / ?quantity=-5 차감 (판매 시작 후에는 추가만 가능)
    @PutMapping("/product/stock/{prdId}")
    public Integer adjustStock(@PathVariable("prdId") Long prdId, @RequestParam("quantity") Integer quantity) {
        return service.adjustStock(prdId, quantity); // 조정 후 재고 반환
    }

    // 상품 삭제
    @DeleteMapping("/product/delete/{prdId}")
    public void deleteProduct(@PathVariable("prdId") Long prdId) {
        service.deleteProduct(prdId);
    }
}
