package com.popq.platform.controller;

import com.popq.platform.dto.ImageResponse;
import com.popq.platform.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ImageController {

    ImageService service;

    @Autowired
    public ImageController(ImageService service) {
        this.service = service;
    }

    // 이미지 업로드 (/api/admin/** 이라 ADMIN만 호출 가능)
    // form-data, key = file
    // 응답의 url을 팝업 storeImg · 상품 productImg에 넣어서 등록/수정
    @PostMapping(value = "/api/admin/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageResponse uploadImage(@RequestParam(value = "file", required = false) MultipartFile file) {
        return service.uploadImage(file);
    }
}