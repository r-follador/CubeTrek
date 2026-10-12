package com.cubetrek.upload.garminconnect;

import com.cubetrek.database.UserThirdpartyConnectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

// Imported only by the standalone CLI, never discovered during web application startup.
public class GarminOAuth2MigrationCommand {
    private static final Logger logger = LoggerFactory.getLogger(GarminOAuth2MigrationCommand.class);
    private final UserThirdpartyConnectRepository repository;
    private final GarminOAuth2MigrationService migration;
    private final GarminApiClient api;

    public GarminOAuth2MigrationCommand(UserThirdpartyConnectRepository repository,
                                        GarminOAuth2MigrationService migration, GarminApiClient api) {
        this.repository = repository;
        this.migration = migration;
        this.api = api;
    }

    public int run(boolean dryRun, int limit, Long connectionId) throws java.io.IOException {
        if (limit < 0) throw new IllegalArgumentException("Migration limit must be nonnegative");
        List<Long> candidates = repository.findGarminMigrationCandidates().stream()
                .filter(id -> connectionId == null || connectionId.equals(id))
                .limit(limit == 0 ? Long.MAX_VALUE : limit).toList();
        logger.info("Garmin migration: candidates={}, dryRun={}", candidates.size(), dryRun);
        if (dryRun) {
            for (Long id : candidates) logger.info("Garmin migration candidate: connection={}", id);
            return 0;
        }
        api.oauth2ClientId();
        int failed = 0;
        for (Long id : candidates) {
            var result = migration.migrate(id);
            logger.info("Garmin migration: connection={}, status={}, detail={}", id, result.status(), result.detail());
            if (result.status() == GarminOAuth2MigrationService.Status.FAILED) failed++;
            if (Thread.currentThread().isInterrupted()) return 1;
        }
        return failed == 0 ? 0 : 1;
    }
}
