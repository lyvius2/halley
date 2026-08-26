package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.SystemConfigRepository;
import banghak.home.halley.batch.ListingCheckJob;
import banghak.home.halley.domain.setting.SystemConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.util.TimeZone;

@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    private static final String CRON_KEY = "batch.listingCheck.cron";
    private static final String DEFAULT_CRON = "0 0 9 * * *";

    private final ListingCheckJob listingCheckJob;
    private final SystemConfigRepository systemConfigRepository;

    public SchedulingConfig(ListingCheckJob listingCheckJob,
                            SystemConfigRepository systemConfigRepository) {
        this.listingCheckJob = listingCheckJob;
        this.systemConfigRepository = systemConfigRepository;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(listingCheckJob::run, context -> {
            final String cron = systemConfigRepository.findById(CRON_KEY)
                    .map(SystemConfig::configValue)
                    .filter(value -> value != null && !value.isBlank())
                    .orElse(DEFAULT_CRON);
            return new CronTrigger(cron, TimeZone.getTimeZone("Asia/Seoul")).nextExecution(context);
        });
    }
}
