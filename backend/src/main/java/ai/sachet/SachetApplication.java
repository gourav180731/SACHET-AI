package ai.sachet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** SACHET AI backend entrypoint (Java / Spring Boot 3). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SachetApplication {
  public static void main(String[] args) {
    loadDotEnv();
    SpringApplication.run(SachetApplication.class, args);
  }

  static void loadDotEnv() {
    List<Path> candidates = List.of(
        Path.of(".env"),
        Path.of("..", ".env"),
        Path.of(System.getProperty("user.dir", "."), ".env"),
        Path.of(System.getProperty("user.dir", "."), "..", ".env")
    );
    for (Path p : candidates) {
      if (Files.isRegularFile(p)) {
        try {
          for (String line : Files.readAllLines(p)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
              continue;
            }
            int eq = line.indexOf('=');
            if (eq > 0) {
              String key = line.substring(0, eq).trim();
              String val = line.substring(eq + 1).trim();
              if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                val = val.substring(1, val.length() - 1);
              }
              if (System.getProperty(key) == null && System.getenv(key) == null) {
                System.setProperty(key, val);
              }
            }
          }
          break;
        } catch (Exception ignored) {
        }
      }
    }
  }
}
