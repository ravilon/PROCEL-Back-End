package com.procel.ingestion.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayMigrationConfig {

    @Bean
    FlywayMigrationStrategy procelFlywayMigrationStrategy() {
        return flyway -> {
            System.out.println("[FlywayMigrationConfig] Running Flyway migrations from: "
                    + String.join(",", flyway.getConfiguration().getLocationsAsStrings()));
            flyway.migrate();
        };
    }

    @Bean
    @ConditionalOnMissingBean(Flyway.class)
    Flyway procelFlyway(
            DataSource dataSource,
            @Value("${spring.flyway.locations:classpath:db/migration}") String locations,
            @Value("${spring.flyway.baseline-on-migrate:true}") boolean baselineOnMigrate,
            @Value("${spring.flyway.baseline-version:1}") String baselineVersion,
            @Value("${spring.flyway.encoding:UTF-8}") String encoding
    ) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(locations.split("\\s*,\\s*"))
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(baselineVersion)
                .encoding(encoding)
                .load();
    }

    @Bean
    @ConditionalOnMissingBean(FlywayMigrationInitializer.class)
    FlywayMigrationInitializer procelFlywayMigrationInitializer(
            Flyway flyway,
            FlywayMigrationStrategy strategy
    ) {
        return new FlywayMigrationInitializer(flyway, strategy);
    }
}
