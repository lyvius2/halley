package banghak.home.halley.batch;

import banghak.home.halley.adapter.outbound.persistence.ListingCheckLogRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.application.service.NotificationService;
import banghak.home.halley.domain.property.ListingCheckLog;
import banghak.home.halley.domain.property.ListingCheckResult;
import banghak.home.halley.domain.property.ListingAliveChecker;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class ListingCheckJob {

    private static final String CRON_KEY = "batch.listingCheck.cron";
    private static final String FAIL_THRESHOLD_KEY = "batch.listingCheck.failThreshold";

    private final PropertyRepository propertyRepository;
    private final ListingCheckLogRepository listingCheckLogRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final ListingAliveChecker checker;
    private final NotificationService notificationService;
    private final long delayMaxMs;

    public ListingCheckJob(PropertyRepository propertyRepository,
                           ListingCheckLogRepository listingCheckLogRepository,
                           SystemConfigRepository systemConfigRepository,
                           ListingAliveChecker checker,
                           NotificationService notificationService,
                           @Value("${batch.listingCheck.delay-max-ms:0}") long delayMaxMs) {
        this.propertyRepository = propertyRepository;
        this.listingCheckLogRepository = listingCheckLogRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.checker = checker;
        this.notificationService = notificationService;
        this.delayMaxMs = delayMaxMs;
    }

    public void run() {
        final int failThreshold = intConfig(FAIL_THRESHOLD_KEY, 3);
        final List<Property> targets = propertyRepository.findBatchTargets();
        if (targets.isEmpty()) {
            log.info("[listing-check] No target properties - job finished.");
            return;
        }

        final List<Property> alive = new ArrayList<>();
        final List<Property> gone = new ArrayList<>();
        int errorCount = 0;
        boolean blocked = false;

        for (final Property property : targets) {
            sleepRandom();
            final long start = System.nanoTime();
            final ListingCheckResult result = checker.check(property.sourceUrl());
            final int elapsedMs = (int) ((System.nanoTime() - start) / 1_000_000);
            listingCheckLogRepository.save(new ListingCheckLog(
                    null, property.id(), Instant.now(), result.httpStatus(),
                    result.verdict(), result.evidence(), elapsedMs, false));
            switch (result.verdict()) {
                case BLOCKED -> blocked = true;
                case ALIVE -> alive.add(property);
                case GONE -> gone.add(property);
                case ERROR -> errorCount++;
            }
            if (blocked) {
                break;
            }
        }

        if (blocked) {
            log.warn("[listing-check] Bot-blocked (403/429) - aborting job.");
            notificationService.sendBatchBlocked();
            return;
        }

        final int checked = alive.size() + gone.size() + errorCount;
        if (gone.size() >= 2 && gone.size() * 2 > checked) {
            log.warn("[listing-check] Majority GONE ({}/{}) - circuit opened, no status changes.", gone.size(), checked);
            notificationService.sendBatchCircuitOpen();
            return;
        }

        final List<Property> soldOut = new ArrayList<>();
        for (final Property property : alive) {
            propertyRepository.updateListingStatus(property.id(), ListingStatus.ACTIVE, true, 0, null);
        }
        for (final Property property : gone) {
            final int streak = property.checkFailStreak() + 1;
            if (streak >= failThreshold) {
                propertyRepository.updateListingStatus(property.id(), ListingStatus.SOLD_OUT, false, streak, Instant.now());
                soldOut.add(property);
            } else {
                propertyRepository.updateListingStatus(property.id(), ListingStatus.UNREACHABLE, true, streak, null);
            }
        }

        if (!soldOut.isEmpty()) {
            log.info("[listing-check] Confirmed {} sold-out listings.", soldOut.size());
            notificationService.sendListingsSoldOut(soldOut);
        }
        notificationService.sendBatchSummary(targets.size(), alive.size(), gone.size(), errorCount);
    }

    private void sleepRandom() {
        if (delayMaxMs <= 0) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(delayMaxMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int intConfig(String key, int defaultValue) {
        return systemConfigRepository.findById(key)
                .map(c -> {
                    try {
                        return Integer.parseInt(c.configValue());
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }
}
