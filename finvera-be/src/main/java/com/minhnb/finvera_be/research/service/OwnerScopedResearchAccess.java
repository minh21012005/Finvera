package com.minhnb.finvera_be.research.service;

import com.minhnb.finvera_be.auth.config.OwnerProperties;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Shared owner-scoping enforcement primitive for research module (SEC-001, SEC-002, research R-002).
 * Compares a resource's {@code owner_id} against the session's authenticated owner id
 * and treats a mismatch identically to "not found" (never a distinguishable 403).
 */
@Component
public class OwnerScopedResearchAccess {

    private final OwnerProperties ownerProperties;

    public OwnerScopedResearchAccess(OwnerProperties ownerProperties) {
        this.ownerProperties = Objects.requireNonNull(ownerProperties, "ownerProperties");
    }

    public UUID getAuthenticatedOwnerId() {
        return ownerProperties.id();
    }

    public boolean isCurrentOwner(UUID ownerId) {
        return ownerId != null && ownerProperties.id().equals(ownerId);
    }

    /**
     * Filters a resource lookup result by {@code owner_id}: if the resource is present
     * but belongs to a different owner, returns {@link Optional#empty()}, identical
     * to when the resource does not exist.
     */
    public <T> Optional<T> filterOwned(Optional<T> resource, Function<T, UUID> ownerIdExtractor) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(ownerIdExtractor, "ownerIdExtractor");
        return resource.filter(r -> isCurrentOwner(ownerIdExtractor.apply(r)));
    }

    /**
     * Asserts that the resource owner matches the authenticated owner.
     */
    public boolean checkOwnership(UUID resourceOwnerId) {
        return isCurrentOwner(resourceOwnerId);
    }
}
