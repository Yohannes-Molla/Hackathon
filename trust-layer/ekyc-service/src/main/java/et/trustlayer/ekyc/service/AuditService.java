package et.trustlayer.ekyc.service;

import et.trustlayer.common.entity.AuditLog;
import et.trustlayer.common.entity.Tenant;
import et.trustlayer.ekyc.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(Tenant tenant, String actor, String action,
                    String resourceType, String resourceId, String details) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .tenant(tenant)
                    .actor(actor)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .ipAddress(extractIpAddress())
                    .details(details)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to write audit log for action={}: {}", action, e.getMessage());
        }
    }

    private String extractIpAddress() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest request = sra.getRequest();
                String xff = request.getHeader("X-Forwarded-For");
                return xff != null ? xff.split(",")[0].trim() : request.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }
}
