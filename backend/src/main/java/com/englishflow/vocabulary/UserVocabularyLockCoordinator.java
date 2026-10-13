package com.englishflow.vocabulary;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Database-coordinated concurrency lock for vocabulary mutations.
 *
 * Employs transaction-scoped PostgreSQL advisory locks (pg_advisory_xact_lock)
 * using a stable 64-bit key uniquely derived from the authenticated User UUID.
 *
 * Guarantees:
 * 1. Database-engine level lock shared across ALL Spring Boot instances.
 * 2. Held strictly until the enclosing database transaction commits or rolls back.
 * 3. Released automatically by PostgreSQL on commit or rollback; zero leaks.
 * 4. Serializes concurrent Bulk Imports and Quick Adds for the same user.
 * 5. Does not block operations for other distinct users (per-user concurrency isolation).
 * 6. Zero unbounded in-memory map allocations.
 */
@Component
public class UserVocabularyLockCoordinator {

    private static final Logger log = LoggerFactory.getLogger(UserVocabularyLockCoordinator.class);

    // Domain salt: 0x564F4341424C4F43L (ASCII for 'VOCABLOC')
    private static final long VOCAB_LOCK_SALT = 0x564F4341424C4F43L;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Compute a deterministic 64-bit integer lock key from the UUID.
     */
    public static long calculateLockKey(UUID userId) {
        long msb = userId.getMostSignificantBits();
        long lsb = userId.getLeastSignificantBits();
        return (msb ^ lsb) ^ VOCAB_LOCK_SALT;
    }

    /**
     * Acquires the transaction-scoped PostgreSQL advisory lock.
     * Blocks until any conflicting transaction for the same user commits or rolls back.
     * Requires an active database transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquireUserVocabularyLock(UUID userId) {
        long lockKey = calculateLockKey(userId);
        log.debug("Acquiring transaction-scoped vocabulary advisory lock for user {} (key={})", userId, lockKey);
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:lockKey)")
            .setParameter("lockKey", lockKey)
            .getResultList();
        log.debug("Acquired transaction-scoped vocabulary advisory lock for user {}", userId);
    }
}
