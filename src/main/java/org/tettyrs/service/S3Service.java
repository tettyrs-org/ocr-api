package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;

@ApplicationScoped
public class S3Service {
    @Inject
    S3Client s3Client;

    @ConfigProperty(name = "tettyrs.s3.bucket")
    String bucketName;

    // Upload
    public String uploadFile(String key, InputStream fileStream, Long fileSize, String contentType){
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(fileSize)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(fileStream, fileSize));
            return buildS3Path(key);
        }catch (S3Exception e){
            throw new RuntimeException("S3 upload failed: "+e.getMessage(), e);
        }
    }

    //download
    public InputStream downloadFile(String key){
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            return s3Client.getObject(getRequest);
        }catch (NoSuchKeyException e){
            throw new IllegalArgumentException("File not found: "+key);
        }catch (S3Exception e){
            throw new RuntimeException("S3 download failed: "+e.getMessage(), e);
        }
    }

    //delete
    public void deleteFile(String key){
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteRequest);
        }catch (S3Exception e){
            throw new RuntimeException("S3 delete failed: "+e.getMessage(), e);
        }
    }

    //check exist file
    public boolean fileExists(String key){
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.headObject(headRequest);
            return true;
        }catch (NoSuchKeyException e){
            return false;
        }catch (S3Exception e){
            throw new RuntimeException("S3 check failed: "+e.getMessage(), e);
        }
    }


    //build s3 path string = s3://bucket/key
    private String buildS3Path(String key){
        return "s3://" +bucketName+ "/" +key;
    }

    //extract key from s3 path
    public String extractKeyFromPath(String s3Path){
        // s3://bucket/key -> key
        return s3Path.replace("s3://"+bucketName+"/", "");
    }

}
