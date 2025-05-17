package com.example.rosys.service;

import com.example.rosys.enums.PolygonCreationType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Service
public interface GridService {


    File getRasterData(Integer firstBoundary, Integer secondBoundary, PolygonCreationType type) throws IOException;

    String getUrlIfExistsAndCreateNewIfNot(MultipartFile file) throws IOException;
}
