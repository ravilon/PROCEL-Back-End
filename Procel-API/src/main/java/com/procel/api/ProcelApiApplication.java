package com.procel.api;

import java.sql.SQLException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.procel.api.integration.cobalto.CobaltoProperties;


@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties(CobaltoProperties.class)
public class ProcelApiApplication {

	public static void main(String[] args) {
		try {
			SpringApplication.run(ProcelApiApplication.class, args);
		} catch (RuntimeException ex) {
			if (isFatalDatabaseStartupFailure(ex)) {
				System.err.println("[Startup] Fatal database authentication/permission failure. "
						+ "Check SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, and SPRING_DATASOURCE_PASSWORD. "
						+ "The application will stop instead of retrying the database connection.");
				System.exit(1);
			}
			throw ex;
		}
	}

	private static boolean isFatalDatabaseStartupFailure(Throwable ex) {
		for (Throwable current = ex; current != null; current = current.getCause()) {
			if (current instanceof SQLException sqlException) {
				String sqlState = sqlException.getSQLState();
				if (sqlState != null && (sqlState.startsWith("28") || sqlState.equals("42501"))) {
					return true;
				}
			}

			String message = current.getMessage();
			if (message != null && isFatalDatabaseMessage(message.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	private static boolean isFatalDatabaseMessage(String message) {
		return message.contains("password authentication failed")
				|| message.contains("authentication failed")
				|| message.contains("permission denied")
				|| message.contains("access denied");
	}

}
