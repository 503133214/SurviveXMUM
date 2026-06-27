package wiki.xmum.config;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MinioConfigTest {
    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(MinioConfig.class);

    @Test
    void doesNotCreateClientWhenStorageIsDisabled() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(MinioClient.class));
    }

    @Test
    void createsClientWhenStorageIsEnabledAndCredentialsExist() {
        contextRunner
            .withPropertyValues(
                "wiki.storage.enabled=true",
                "wiki.storage.endpoint=http://localhost:9000",
                "wiki.storage.access-key=minioadmin",
                "wiki.storage.secret-key=minioadmin",
                "wiki.storage.bucket=wiki",
                "wiki.storage.public-url=http://localhost:9000/wiki"
            )
            .run(context -> assertThat(context).hasSingleBean(MinioClient.class));
    }
}
