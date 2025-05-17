package com.example.rosys.controller;

import com.example.rosys.enums.PolygonCreationType;
import com.example.rosys.service.GridService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/grids")
public class GridController {

    private final GridService gridService;


    @GetMapping("/raster")
    public ResponseEntity<FileSystemResource> getRaster(@RequestParam(value = "firstBoundary", required = false) Integer firstBoundary,
                                                                 @RequestParam(value = "secondBoundary", required = false) Integer secondBoundary,
                                                                 @RequestParam(value = "type", required = false) PolygonCreationType type) throws IOException {

        if (firstBoundary != null && (firstBoundary < 0 || firstBoundary > 255)) {
            throw new IllegalArgumentException("firstBoundary must be between 0 and 255");
        }
        if (secondBoundary != null && (secondBoundary < 0 || secondBoundary > 255)) {
            throw new IllegalArgumentException("secondBoundary must be between 0 and 255");
        }
        if (firstBoundary != null && secondBoundary != null && secondBoundary <= firstBoundary) {
            throw new IllegalArgumentException("secondBoundary must be greater than firstBoundary");
        }

        File file = gridService.getRasterData(firstBoundary, secondBoundary, type);
        FileSystemResource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=output.geojson")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.length())
                .body(resource);
    }

    @PostMapping(value = "/geojson-url", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> getGeoJsonViewerUrl(@RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        return ResponseEntity.ok(gridService.getUrlIfExistsAndCreateNewIfNot(file));
    }

    }




