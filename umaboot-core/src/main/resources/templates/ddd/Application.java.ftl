package ${basePackage};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
<#if isJpa && anyAuditable>
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
</#if>

@SpringBootApplication
<#if isJpa && anyAuditable>
@EnableJpaAuditing<#if anyHasAuditUser>(auditorAwareRef = "auditorAware")</#if>
</#if>
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
