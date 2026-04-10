package com.meridian.retail.repository;

import com.meridian.retail.entity.ChangeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ChangeEventRepository extends JpaRepository<ChangeEvent, Long> {
    long countByEventTypeAndOccurredAtAfter(String eventType, LocalDateTime after);
}
