package com.popq.platform.service;

import com.popq.platform.dto.ImageResponse;
import com.popq.platform.exception.ImageErrorCode;
import com.popq.platform.exception.ImageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class ImageService {

    // 허용 확장자
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp", "gif");

    // 이미지 조회 URL 앞부분 (WebConfig의 경로와 같아야 함)
    public static final String URL_PREFIX = "/uploads/";

    String uploadDir;

    @Autowired
    public ImageService(@Value("${file.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    // 이미지 업로드
    public ImageResponse uploadImage(MultipartFile file) {
        // 1. 파일이 비어 있는지 확인
        if (file == null || file.isEmpty()) {
            throw new ImageException(ImageErrorCode.IMAGE_EMPTY);
        }

        // 2. 확장자 · 형식 확인
        String extension = getExtension(file.getOriginalFilename());
        String contentType = file.getContentType();
        if (!ALLOWED_EXTENSIONS.contains(extension)
                || contentType == null || !contentType.startsWith("image/")) {
            throw new ImageException(ImageErrorCode.INVALID_IMAGE_TYPE);
        }

        // 3. 저장할 파일명 : UUID + 확장자 (이름 겹침 · 한글 파일명 문제 방지)
        String saveName = UUID.randomUUID() + "." + extension;

        // 4. 폴더에 저장
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir); // 폴더가 없으면 생성
            file.transferTo(dir.resolve(saveName));
        } catch (IOException e) {
            throw new ImageException(ImageErrorCode.IMAGE_UPLOAD_FAILED);
        }

        // 5. 조회 경로 반환
        return new ImageResponse(URL_PREFIX + saveName);
    }

    // 파일명에서 확장자만 꺼내기 (소문자로)
    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}