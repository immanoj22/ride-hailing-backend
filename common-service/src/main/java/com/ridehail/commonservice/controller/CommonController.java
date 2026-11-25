package com.ridehail.commonservice.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ridehail.common.response.ApiResponseService;
import com.ridehail.commonservice.dto.GoogleVerifyRequest;
import com.ridehail.commonservice.service.GoogleAuthService;
import com.ridehail.commonservice.service.ImageUploadService;
import com.ridehail.commonservice.util.ImageFolder;

import jakarta.validation.Valid;

/** Direct port of controllers/common.controller.js. */
@RestController
@RequestMapping("/common-apis")
public class CommonController {

    private final ImageUploadService imageUploadService;
    private final GoogleAuthService googleAuthService;
    private final ApiResponseService apiResponseService;

    public CommonController(ImageUploadService imageUploadService, GoogleAuthService googleAuthService,
            ApiResponseService apiResponseService) {
        this.imageUploadService = imageUploadService;
        this.googleAuthService = googleAuthService;
        this.apiResponseService = apiResponseService;
    }

    @PostMapping(value = "/image-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> imageUpload(
            @RequestParam("imageFiles") List<MultipartFile> imageFiles,
            @RequestParam(value = "folderArr", required = false) List<String> folderArr,
            @RequestParam(value = "subFolderArr", required = false) List<String> subFolderArr,
            @RequestParam(value = "allSame", required = false, defaultValue = "false") String allSame,
            @RequestParam(value = "sameFolderName", required = false) String sameFolderName) {
        List<String> urls = imageUploadService.upload(imageFiles, folderArr, subFolderArr, "true".equals(allSame), sameFolderName);
        return apiResponseService.ok("Uploaded successfully", urls);
    }

    @GetMapping("/get-image-directories")
    public ResponseEntity<?> getFolderDirs() {
        return apiResponseService.ok("Folder directories fetched.", ImageFolder.all());
    }

    @PostMapping("/google-verify")
    public ResponseEntity<?> verifyGoogleToken(@Valid @org.springframework.web.bind.annotation.RequestBody GoogleVerifyRequest request) {
        Map<String, Object> payload = googleAuthService.verify(request.getToken());
        return apiResponseService.ok("Google token verified successfully.", payload);
    }
}
