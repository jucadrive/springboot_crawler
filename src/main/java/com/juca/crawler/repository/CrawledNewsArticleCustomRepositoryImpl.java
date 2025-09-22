package com.juca.crawler.repository;

import com.juca.crawler.dto.ArticleDto;
import com.juca.crawler.dto.QArticleDto;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static com.juca.crawler.domain.QCrawledNewsArticle.crawledNewsArticle;

@Repository
@RequiredArgsConstructor
public class CrawledNewsArticleCustomRepositoryImpl implements CrawledNewsArticleCustomRepository {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public ArticleDto findByArticleId(Long id) {
        return jpaQueryFactory.select(
                new QArticleDto(crawledNewsArticle.media,
                        crawledNewsArticle.category,
                        crawledNewsArticle.title,
                        crawledNewsArticle.article,
                        crawledNewsArticle.author,
                        crawledNewsArticle.publishedAt)
        )
                .from(crawledNewsArticle)
                .where(crawledNewsArticle.id.eq(id))
                .fetchFirst();
    }
}
