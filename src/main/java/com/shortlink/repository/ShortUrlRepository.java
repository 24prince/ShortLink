package com.shortlink.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.shortlink.domain.ShortUrl;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    /** Atomic in-database increment, so concurrent flushes never lose updates. */
    @Transactional
    @Modifying
    @Query("update ShortUrl u set u.clickCount = u.clickCount + :n where u.shortCode = :code")
    int addClicks(@Param("code") String code, @Param("n") long n);
}
