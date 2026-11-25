package com.ridehail.commonservice.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ridehail.common.exception.ApiException;
import com.ridehail.commonservice.util.ImageFolder;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Direct port of {@code image_upload_S3} (controllers/common.controller.js): every file in
 * {@code imageFiles} is uploaded either to one shared folder ({@code allSame=true} +
 * {@code sameFolderName}) or to a per-file folder ({@code folderArr}, same length as the files).
 */
@Service
public class ImageUploadService {

    private final S3Client s3Client;
    private final String bucketName;
    private final String region;

    public ImageUploadService(
            S3Client s3Client,
            @Value("${ridehail.s3.bucket-name}") String bucketName,
            @Value("${ridehail.s3.region}") String region) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.region = region;
    }

    public List<String> upload(List<MultipartFile> files, List<String> folderArr, List<String> subFolderArr,
            boolean allSame, String sameFolderName) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(400, "No files uploaded.");
        }
        if (allSame) {
            if (sameFolderName == null || sameFolderName.isBlank()) {
                throw new ApiException(400, "sameFolderName is required.");
            }
            if (!ImageFolder.isValidKey(sameFolderName)) {
                throw new ApiException(400, "sameFolderName should be valid. Please refer the /get-image-directories api.");
            }
        } else {
            if (folderArr == null || folderArr.size() != files.size()) {
                throw new ApiException(400, "folderArr and imageFiles needs to be the same length.");
            }
            if (!folderArr.stream().allMatch(ImageFolder::isValidKey)) {
                throw new ApiException(400, "folderArr only allows the /get-image-directories api.");
            }
        }

        List<String> urls = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String subFolder = subFolderArr != null && i < subFolderArr.size() ? subFolderArr.get(i) : null;
            String basePath = allSame ? ImageFolder.pathFor(sameFolderName) : ImageFolder.pathFor(folderArr.get(i));
            String fileName = System.currentTimeMillis() + "_" + sanitizeFilename(file.getOriginalFilename());
            String key = basePath + (subFolder == null || subFolder.isBlank() ? "" : "/" + subFolder) + "/" + fileName;

            try {
                s3Client.putObject(
                        PutObjectRequest.builder().bucket(bucketName).key(key).contentType(file.getContentType()).build(),
                        RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            } catch (Exception e) {
                throw new ApiException(500, "Failed to upload file: " + file.getOriginalFilename());
            }

            urls.add("https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key);
        }
        return urls;
    }

    private String sanitizeFilename(String input) {
        if (input == null) {
            return "file";
        }
        return input.toLowerCase()
                .trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9._-]", "");
    }
}
