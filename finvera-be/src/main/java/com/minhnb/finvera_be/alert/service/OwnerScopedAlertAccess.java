package com.minhnb.finvera_be.alert.service;
import com.minhnb.finvera_be.auth.config.OwnerProperties;import java.util.UUID;import org.springframework.stereotype.Component;
@Component public class OwnerScopedAlertAccess{private final OwnerProperties owner;public OwnerScopedAlertAccess(OwnerProperties o){owner=o;}public UUID getAuthenticatedOwnerId(){return owner.id();}}
