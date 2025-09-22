package com.juca.crawler.controller;

import com.juca.crawler.dto.SummarizeReqDto;
import com.juca.crawler.dto.SummarizeResDto;
import com.juca.crawler.service.SummarizeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SummarizeController {

    private final SummarizeService summarizeService;

    @GetMapping("/summary")
    public ResponseEntity<SummarizeResDto> getSummarizeRes(Long id) {
        SummarizeResDto resDto = summarizeService.getSummarizedArticleAndKeywords(id);
        return new ResponseEntity<>(resDto, HttpStatus.OK);
    }
}
