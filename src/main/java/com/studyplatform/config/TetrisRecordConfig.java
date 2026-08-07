package com.studyplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.service.TetrisRecordService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * 서바이벌 전적 저장소
 *
 * 서바이벌 랭크는 대전 랭크와 완전히 따로 매긴다. 계산 방식(Elo·배치·티어)은 똑같으니
 * 클래스를 복제하지 않고 같은 서비스를 다른 파일 경로로 한 번 더 띄운다.
 */
@Configuration
public class TetrisRecordConfig {

    @Bean("tetrisSurvivalRecordService")
    public TetrisRecordService tetrisSurvivalRecordService(
            ObjectMapper objectMapper,
            @Value("${tetris.survival.records.path:data/tetris-survival-records.json}") String recordPath
    ) {
        return new TetrisRecordService(objectMapper, Path.of(recordPath));
    }
}
