package com.englishflow.vocabulary;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface FolderRepository extends JpaRepository<Folder, UUID> {
    Optional<Folder> findByIdAndUserId(UUID id, UUID userId);
    Page<Folder> findByUserId(UUID userId, Pageable pageable);

    @Query(value = """
        SELECT EXISTS(SELECT 1 FROM folders WHERE user_id = :userId
            AND name_key = translate(:name, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')
            AND (:excluded IS NULL OR id <> CAST(:excluded AS uuid)))
        """, nativeQuery = true)
    boolean nameExists(@Param("userId") UUID userId, @Param("name") String name,
                       @Param("excluded") String excluded);

    interface SetCount { UUID getFolderId(); long getSetCount(); }
    @Query("""
        SELECT c.folderId AS folderId, COUNT(c) AS setCount FROM Collection c
        WHERE c.userId = :userId AND c.folderId IN :ids GROUP BY c.folderId
        """)
    List<SetCount> countStudySets(@Param("userId") UUID userId, @Param("ids") List<UUID> ids);
}
