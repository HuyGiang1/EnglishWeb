package com.englishflow.vocabulary;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, UUID> {

    Optional<Collection> findByIdAndUserId(UUID id, UUID userId);

    Page<Collection> findByUserId(UUID userId, Pageable pageable);

    boolean existsByUserIdAndTitleIgnoreCase(UUID userId, String title);

    boolean existsByUserIdAndTitleIgnoreCaseAndIdNot(UUID userId, String title, UUID id);

    Page<Collection> findByUserIdAndFolderId(UUID userId, UUID folderId, Pageable pageable);
    Page<Collection> findByUserIdAndFolderIdIsNull(UUID userId, Pageable pageable);
    long countByUserIdAndFolderId(UUID userId, UUID folderId);

    long countByUserId(UUID userId);
}
