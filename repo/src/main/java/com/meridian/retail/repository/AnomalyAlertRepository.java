package com.meridian.retail.repository;

import com.meridian.retail.entity.AnomalyAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnomalyAlertRepository extends JpaRepository<AnomalyAlert, Long> {
    List<AnomalyAlert> findByAcknowledgedAtIsNullOrderByDetectedAtDesc();
    List<AnomalyAlert> findAllByOrderByDetectedAtDesc();
    long countByAcknowledgedAtIsNull();
}
