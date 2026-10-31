package org.tickethub.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tickethub.notificationservice.entity.DltMessage;

import java.util.List;

@Repository
public interface DltMessageRepository extends JpaRepository<DltMessage, Long> {
    List<DltMessage> findByStatusOrderByCreatedAtDesc(String status);
}
