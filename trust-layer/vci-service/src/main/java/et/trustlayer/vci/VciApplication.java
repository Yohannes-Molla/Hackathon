package et.trustlayer.vci;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "et.trustlayer.common.entity")
@EnableJpaRepositories(basePackages = "et.trustlayer.vci.repository")
public class VciApplication {
    public static void main(String[] args) {
        SpringApplication.run(VciApplication.class, args);
    }
}
