package com.juca.crawler.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.juca.crawler")
public class QuerydslConfig {

    @PersistenceContext // EntityManager에 의존성 주입 담당
    private EntityManager entityManager;    // EntityManager는 JPA에서 생성, 조회, 수정, 삭제를 수행

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        // 쿼리를 작성하는 JPAQueryFactory에 EntityManager를 넘겨서 사용
        return new JPAQueryFactory(entityManager);
    }
}
