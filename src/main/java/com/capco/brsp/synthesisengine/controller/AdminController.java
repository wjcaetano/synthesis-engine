package com.capco.brsp.synthesisengine.controller;

import com.capco.brsp.synthesisengine.dto.ResponseAboutDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    @Value("${about.appName}")
    private String appName;

    @Value("${about.appCurrentVersion}")
    private String appCurrentVersion;

    @GetMapping("/about")
    public ResponseEntity<ResponseAboutDto> about() {
        return ResponseEntity.ok(
                ResponseAboutDto.builder()
                        .appName(appName)
                        .appCurrentVersion(appCurrentVersion)
                        .apiLatestVersion("v1")
                        .build()
        );
    }
}
