package et.trustlayer.ekyc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "et.trustlayer.common.entity")
@EnableJpaRepositories(basePackages = "et.trustlayer.ekyc.repository")
public class EkycApplication {
    public static void main(String[] args) {
        SpringApplication.run(EkycApplication.class, args);
    }
}
