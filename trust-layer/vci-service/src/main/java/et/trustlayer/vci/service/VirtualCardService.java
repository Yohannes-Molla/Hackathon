package et.trustlayer.vci.service;

import et.trustlayer.common.entity.BiometricCredential;
import et.trustlayer.common.entity.Merchant;
import et.trustlayer.common.entity.UserIdentity;
import et.trustlayer.common.entity.VirtualCard;
import et.trustlayer.vci.repository.BiometricCredentialRepository;
import et.trustlayer.vci.repository.MerchantRepository;
import et.trustlayer.vci.repository.TenantRepository;
import et.trustlayer.vci.repository.UserIdentityRepository;
import et.trustlayer.vci.repository.VirtualCardRepository;
import et.trustlayer.vci.dto.ProvisionCardRequest;
import et.trustlayer.vci.dto.VirtualCardResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VirtualCardService {

    private final UserIdentityRepository userRepository;
    private final BiometricCredentialRepository credentialRepository;
    private final VirtualCardRepository virtualCardRepository;
    private final MerchantRepository merchantRepository;
    private final TenantRepository tenantRepository;
    private final RestTemplate restTemplate;

    @Value("${trustlayer.vault.base-url:http://localhost:8200}")
    private String vaultBaseUrl;

    @Value("${trustlayer.vault.token:root}")
    private String vaultToken;

    @Transactional
    public VirtualCardResponse provisionCard(ProvisionCardRequest request) {
        log.info("Provisioning virtual card for user {} and binding to key {}", request.getUserId(), request.getKeyId());

        UserIdentity user = userRepository.findById(UUID.fromString(request.getUserId()))
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!"EKYC_COMPLETE".equals(user.getOnboardingState()) && !"LIVE".equals(user.getOnboardingState())) {
            throw new RuntimeException("User must complete eKYC before card provisioning");
        }

        BiometricCredential credential = credentialRepository.findByKeyId(request.getKeyId())
                .orElseThrow(() -> new RuntimeException("Credential not found for binding"));

        if (!credential.getUserIdentity().getId().equals(user.getId())) {
            throw new RuntimeException("Credential does not belong to the requesting user");
        }

        // Mock Marqeta API Virtual Card Creation
        log.info("Calling external VCI API (e.g. Marqeta) to provision card...");
        try { Thread.sleep(800); } catch (InterruptedException ignored) {}

        String secureVaultToken = tokenizeWithVault("453288210092" + String.format("%04d", (int) (Math.random() * 10000)));
        String lastFour = String.format("%04d", (int)(Math.random() * 10000));

        VirtualCard card = VirtualCard.builder()
                .userIdentity(user)
                .tenant(user.getTenant())
                .boundCredential(credential) // Hard crypto binding FAPI 2.0 concept
                .panToken(secureVaultToken)
                .lastFour(lastFour)
                .cardNetwork("VISA")
                .currency("ETB")
                .dailyLimitMinor(500000L) // 5000.00 ETB
                .singleTxLimitMinor(100000L) // 1000.00 ETB
                .status("ACTIVE")
                .expiryDate(LocalDate.now().plusYears(3))
                .build();

        card = virtualCardRepository.save(card);
        log.info("Successfully provisioned Virtual Card ID: {}", card.getId());

        // Upgrade user status since they have a working card now
        if (!"LIVE".equals(user.getOnboardingState())) {
            user.setOnboardingState("LIVE");
            userRepository.save(user);
        }

        return mapToDto(card);
    }

    @Transactional(readOnly = true)
    public List<VirtualCardResponse> getUserCards(String userId) {
        return virtualCardRepository.findByUserIdentityId(UUID.fromString(userId))
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public String getCurrentDynamicCvv(String cardId) {
        VirtualCard card = virtualCardRepository.findById(UUID.fromString(cardId))
            .orElseThrow(() -> new RuntimeException("Card not found"));
        return computeDynamicCvv(card.getPanToken());
    }

    @Transactional
    public VirtualCardResponse updateLimits(String cardId, Long dailyLimitMinor, Long singleTxLimitMinor) {
        VirtualCard card = virtualCardRepository.findById(UUID.fromString(cardId))
            .orElseThrow(() -> new RuntimeException("Card not found"));
        if (dailyLimitMinor != null) {
            card.setDailyLimitMinor(dailyLimitMinor);
        }
        if (singleTxLimitMinor != null) {
            card.setSingleTxLimitMinor(singleTxLimitMinor);
        }
        return mapToDto(virtualCardRepository.save(card));
    }

    @Transactional
    public VirtualCardResponse updateStatus(String cardId, String status) {
        VirtualCard card = virtualCardRepository.findById(UUID.fromString(cardId))
            .orElseThrow(() -> new RuntimeException("Card not found"));
        card.setStatus(status);
        return mapToDto(virtualCardRepository.save(card));
    }

    @Transactional
    public Merchant registerMerchant(String tenantId, String keycloakSub, String name, String category) {
        Optional<Merchant> existing = merchantRepository.findByKeycloakSub(keycloakSub);
        if (existing.isPresent()) {
            return existing.get();
        }

        var tenant = tenantRepository.findById(UUID.fromString(tenantId))
            .orElseThrow(() -> new RuntimeException("Tenant not found"));

        Merchant merchant = Merchant.builder()
            .tenant(tenant)
            .keycloakSub(keycloakSub)
            .name(name)
            .category(category)
            .status("ACTIVE")
            .onboardingState("APPROVED")
            .build();
        return merchantRepository.save(merchant);
    }

    @Transactional(readOnly = true)
    public Merchant getMerchant(String merchantId) {
        return merchantRepository.findById(UUID.fromString(merchantId))
            .orElseThrow(() -> new RuntimeException("Merchant not found"));
    }

    private String tokenizeWithVault(String cardNumber) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Vault-Token", vaultToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            String plaintext = java.util.Base64.getEncoder().encodeToString(cardNumber.getBytes(StandardCharsets.UTF_8));
            Map<String, String> payload = Map.of("plaintext", plaintext);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                vaultBaseUrl + "/v1/transit/encrypt/trust-layer-pan",
                new HttpEntity<>(payload, headers),
                Map.class
            );

            if (response.getBody() != null && response.getBody().get("data") instanceof Map<?, ?> data) {
                Object ciphertext = data.get("ciphertext");
                if (ciphertext != null) {
                    return String.valueOf(ciphertext);
                }
            }
        } catch (Exception ex) {
            log.warn("Vault tokenization unavailable, using fallback token: {}", ex.getMessage());
        }
        return "vault_tok_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String computeDynamicCvv(String seed) {
        try {
            long timeWindow = System.currentTimeMillis() / 60000;
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(seed.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = hmac.doFinal(String.valueOf(timeWindow).getBytes(StandardCharsets.UTF_8));
            int offset = digest[digest.length - 1] & 0x0F;
            int binary = ((digest[offset] & 0x7F) << 24)
                | ((digest[offset + 1] & 0xFF) << 16)
                | ((digest[offset + 2] & 0xFF) << 8)
                | (digest[offset + 3] & 0xFF);
            int cvv = binary % 1000;
            return String.format("%03d", cvv);
        } catch (Exception e) {
            return "000";
        }
    }

    private VirtualCardResponse mapToDto(VirtualCard card) {
        return VirtualCardResponse.builder()
                .cardId(card.getId().toString())
                .lastFour(card.getLastFour())
                .cardNetwork(card.getCardNetwork())
                .status(card.getStatus())
                .currency(card.getCurrency())
                .dailyLimitMinor(card.getDailyLimitMinor())
                .build();
    }
}
