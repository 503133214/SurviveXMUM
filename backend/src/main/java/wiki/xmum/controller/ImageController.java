package wiki.xmum.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import wiki.xmum.common.ApiResponse;
import wiki.xmum.service.ImageStorageService;

@RestController
@RequestMapping("/wiki")
public class ImageController {
    private final ImageStorageService imageStorageService;

    public ImageController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImageStorageService.UploadedImage> upload(
        @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.ok(imageStorageService.upload(file));
    }
}
