package com.cubetrektools.garmin;

import com.cubetrek.database.AESEncryptionService;
import com.cubetrek.database.UserThirdpartyConnect;
import com.cubetrek.upload.garminconnect.GarminApiClient;
import com.cubetrek.upload.garminconnect.GarminOAuth2MigrationCommand;
import com.cubetrek.upload.garminconnect.GarminOAuth2MigrationService;
import com.cubetrek.upload.garminconnect.GarminOAuth2RefreshCheck;
import com.cubetrek.upload.garminconnect.GarminOAuth2TokenService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration(exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@EntityScan(basePackageClasses = UserThirdpartyConnect.class)
@EnableJpaRepositories(basePackages = "com.cubetrek.database", excludeFilters =
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.cubetrek\\.database\\.(?!UserThirdpartyConnectRepository$).*"))
@Import({AESEncryptionService.class, UserThirdpartyConnect.DataEncryptDecryptConverter.class,
        GarminApiClient.class, GarminOAuth2TokenService.class, GarminOAuth2MigrationService.class,
        GarminOAuth2MigrationCommand.class, GarminOAuth2RefreshCheck.class})
public class GarminMigrationApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(GarminMigrationApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.addInitializers(context -> context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("garminMigrationSafety", Map.of(
                        "spring.jpa.hibernate.ddl-auto", "validate",
                        "spring.sql.init.mode", "never",
                        "spring.flyway.enabled", "false",
                        "spring.liquibase.enabled", "false"))));
        int exitCode;
        try (var context = app.run(args)) {
            var environment = context.getEnvironment();
            boolean dryRun = environment.getProperty("garmin.oauth2.migration.dry-run", Boolean.class, true);
            Long connectionId = environment.getProperty("garmin.oauth2.migration.connection-id", Long.class);
            if (environment.getProperty("garmin.oauth2.migration.verify-refresh", Boolean.class, false)) {
                exitCode = context.getBean(GarminOAuth2RefreshCheck.class).run(dryRun, connectionId);
            } else {
                exitCode = context.getBean(GarminOAuth2MigrationCommand.class).run(dryRun,
                        environment.getProperty("garmin.oauth2.migration.limit", Integer.class, 0), connectionId);
            }
        } catch (Exception e) {
            System.err.println("Garmin migration could not complete (" + e.getClass().getSimpleName() + ").");
            exitCode = 1;
        }
        System.exit(exitCode);
    }
}
