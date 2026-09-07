package banghak.home.halley.config;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class HouseholdBudgetCatalogBootstrap implements ApplicationRunner {
    private final DSLContext dsl;

    public HouseholdBudgetCatalogBootstrap(DSLContext dsl) { this.dsl = dsl; }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        final String sql = new ClassPathResource("household-budget-items-seed.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        int applied = 0;
        for (final String statement : sql.split(";")) {
            if (statement.contains("INSERT INTO household_budget_item_catalog")) {
                dsl.execute(statement);
                applied++;
            }
        }
        log.info("Household budget catalog seed checked. statements={}", applied);
    }
}
