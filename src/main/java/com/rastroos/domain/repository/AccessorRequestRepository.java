package com.rastroos.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rastroos.domain.entity.AccessorRequest;
import com.rastroos.domain.entity.enums.AccessorRequestStatus;

public interface AccessorRequestRepository extends JpaRepository<AccessorRequest, UUID> {

    List<AccessorRequest> findByRequesterUserIdOrderByCreatedAtDesc(UUID requesterUserId);

    List<AccessorRequest> findByStatusOrderByCreatedAtAsc(AccessorRequestStatus status);

    long countByStatus(AccessorRequestStatus status);
}
