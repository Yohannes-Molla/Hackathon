package et.trustlayer.authserver.tenant;

import et.trustlayer.common.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Aspect
@Component
@RequiredArgsConstructor
public class TenantFilterAspect {

    private final EntityManager entityManager;

    @Before("execution(* et.trustlayer..repository.*.*(..))")
    public void enableTenantFilter() {
        if (TenantContext.getTenantId() != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter")
                   .setParameter("tenantId", TenantContext.getTenantId());
        }
    }
}
