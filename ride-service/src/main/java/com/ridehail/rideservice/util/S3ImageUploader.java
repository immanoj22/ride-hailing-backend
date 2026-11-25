package com.ridehail.rideservice.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.ridehail.common.exception.ApiException;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Port of helpers/ride.commonFunction.js {@code uploadImageToS3}. The legacy helper delegated to
 * common-service's /image-upload endpoint which performs a direct S3 PutObject; this does the
 * PutObject directly with the identical resulting URL shape.
 * folderKey "public_vehicle_type" -> "public/vehicletype", "public_vehicle_variant" -> "public/vehiclevariant".
 */
@Component
public class S3ImageUploader {

    private final S3Client s3Client;
    private final String bucketName;
    private final String region;

    public S3ImageUploader(S3Client s3Client,
                           @Value("${ridehail.s3.bucket-name}") String bucketName,
                           @Value("${ridehail.s3.region}") String region) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.region = region;
    }

    private static String pathFor(String folderKey) {
        return switch (folderKey) {
            case "public_vehicle_type" -> "public/vehicletype";
            case "public_vehicle_variant" -> "public/vehiclevariant";
            default -> "public/" + folderKey;
        };
    }

    public String upload(MultipartFile file, String folderKey) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(400, "No file uploaded.");
        }
        String fileName = System.currentTimeMillis() + "_" + sanitize(file.getOriginalFilename());
        String key = pathFor(folderKey) + "/" + fileName;
        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucketName).key(key).contentType(file.getContentType()).build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (Exception e) {
            throw new ApiException(500, "Failed to upload file: " + file.getOriginalFilename());
        }
        return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
    }

    private String sanitize(String input) {
        if (input == null) {
            return "file";
        }
        return input.toLowerCase().trim().replaceAll("\\s+", "_").replaceAll("[^a-z0-9._-]", "");
    }
}
