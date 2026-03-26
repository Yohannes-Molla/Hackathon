package et.trustlayer.vci.service;

import et.trustlayer.common.entity.BiometricCredential;
import et.trustlayer.common.entity.UserIdentity;
import et.trustlayer.common.entity.VirtualCard;
import et.trustlayer.vci.repository.BiometricCredentialRepository;
import et.trustlayer.vci.repository.UserIdentityRepository;
import et.trustlayer.vci.repository.VirtualCardRepository;
import et.trustlayer.vci.dto.ProvisionCardRequest;
import et.trustlayer.vci.dto.VirtualCardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VirtualCardService {

    private final UserIdentityRepository userRepository;
    private final BiometricCredentialRepository credentialRepository;
    private final VirtualCardRepository virtualCardRepository;

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

        // Mock Vault PAN Tokenization
        String secureVaultToken = "vault_tok_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
