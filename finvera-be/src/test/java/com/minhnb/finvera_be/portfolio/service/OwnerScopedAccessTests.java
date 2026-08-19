package com.minhnb.finvera_be.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.minhnb.finvera_be.auth.config.OwnerProperties;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OwnerScopedAccessTests {

    private final UUID authenticatedOwnerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID otherOwnerId = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private OwnerScopedAccess access;

    @BeforeEach
    void setUp() {
        OwnerProperties properties = new OwnerProperties(authenticatedOwnerId, "owner", "$2a$10$hash");
        access = new OwnerScopedAccess(properties);
    }

    @Test
    @DisplayName("Returns authenticated owner id from properties")
    void returnsAuthenticatedOwnerId() {
        assertThat(access.getAuthenticatedOwnerId()).isEqualTo(authenticatedOwnerId);
    }

    @Test
    @DisplayName("Correctly verifies ownership")
    void verifiesOwnership() {
        assertThat(access.isCurrentOwner(authenticatedOwnerId)).isTrue();
        assertThat(access.isCurrentOwner(otherOwnerId)).isFalse();
        assertThat(access.isCurrentOwner(null)).isFalse();
    }

    @Test
    @DisplayName("filterOwned returns present for matching owner")
    void filterOwnedMatchesOwner() {
        record DummyResource(UUID id, UUID ownerId) {}
        DummyResource resource = new DummyResource(UUID.randomUUID(), authenticatedOwnerId);

        Optional<DummyResource> result = access.filterOwned(Optional.of(resource), DummyResource::ownerId);
        assertThat(result).isPresent().contains(resource);
    }

    @Test
    @DisplayName("filterOwned returns empty for wrong owner, identically to nonexistent resource (SEC-001, SEC-002)")
    void filterOwnedTreatsWrongOwnerIdenticalToNonexistent() {
        record DummyResource(UUID id, UUID ownerId) {}
        DummyResource otherResource = new DummyResource(UUID.randomUUID(), otherOwnerId);

        Optional<DummyResource> wrongOwnerResult = access.filterOwned(Optional.of(otherResource), DummyResource::ownerId);
        Optional<DummyResource> nonexistentResult = access.filterOwned(Optional.empty(), DummyResource::ownerId);

        // Both outcomes are identically empty
        assertThat(wrongOwnerResult).isEmpty();
        assertThat(nonexistentResult).isEmpty();
        assertThat(wrongOwnerResult).isEqualTo(nonexistentResult);
    }
}
