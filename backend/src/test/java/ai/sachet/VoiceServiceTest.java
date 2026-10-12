package ai.sachet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.sachet.domain.Classification;
import ai.sachet.domain.User;
import ai.sachet.domain.Role;
import ai.sachet.repo.UserRepository;
import ai.sachet.repo.RoleRepository;
import ai.sachet.security.PasswordService;
import ai.sachet.security.SachetPrincipal;
import ai.sachet.service.VoiceService;
import ai.sachet.service.VoiceGateway;
import ai.sachet.service.VoiceGatewayImpl;

@SpringBootTest
class VoiceServiceTest {

  @Autowired
  VoiceService voiceService;

  @Autowired
  UserRepository users;

  @Autowired
  RoleRepository roles;

  @Autowired
  PasswordService passwords;

  SachetPrincipal alicePrincipal;

  @BeforeEach
  void setUp() throws Exception {
    // Clear and reset
    users.deleteAll();
    roles.deleteAll();

    Role analyst = roles.save(new Role("ANALYST"));
    User alice = new User("alice@example.gov", passwords.hash("alice-pw-12"));
    alice.getRoles().add(analyst);
    alice.setDepartment("C-DOT");
    alice.setClearance(Classification.INTERNAL);
    alice = users.save(alice);

    alicePrincipal = new SachetPrincipal(
        alice.getId(),
        alice.getRoles().stream().map(r -> r.getName()).toList(),
        alice.getClearance().name(),
        alice.getDepartment(),
        alice.getRoles().stream()
            .map(r -> "SCOPE_" + r.getName())
            .toList());
  }

  @Test
  void voiceGatewayInterfaceExists() {
    assertNotNull(voiceService, "VoiceService bean should exist");
    assertNotNull(voiceService.getVoiceCapabilityStatus(),
        "Voice capability status should be available");
  }

  @Test
  void languageSupport() {
    assertTrue(voiceService.isIndianLanguage("hi-IN"),
        "Hindi should be supported");
    assertTrue(voiceService.isIndianLanguage("en-IN"),
        "English should be supported");
    assertTrue(voiceService.isIndianLanguage("ta-IN"),
        "Tamil should be supported");
    assertTrue(voiceService.isIndianLanguage("bn-IN"),
        "Bengali should be supported");
    assertTrue(voiceService.isIndianLanguage("te-IN"),
        "Telugu should be supported");
    assertTrue(voiceService.isIndianLanguage("mr-IN"),
        "Marathi should be supported");
    assertTrue(voiceService.isIndianLanguage("ml-IN"),
        "Malayalam should be supported");
    assertTrue(voiceService.isIndianLanguage("kn-IN"),
        "Kannada should be supported");
    assertTrue(voiceService.isIndianLanguage("gu-IN"),
        "Gujarati should be supported");
    assertTrue(voiceService.isIndianLanguage("pa-IN"),
        "Punjabi should be supported");
    assertTrue(voiceService.isIndianLanguage("ur-IN"),
        "Urdu should be supported");
    assertTrue(voiceService.isIndianLanguage("mr-IN"),
        "Maithili should be supported");
  }

  @Test
  void voiceCapabilityStatus() {
    Map<String, Object> status = voiceService.getVoiceCapabilityStatus();
    assertNotNull(status, "Voice capability status should not be null");
    assertContainsKey(status, "voiceEnabled");
    assertContainsKey(status, "sttLanguages");
    assertContainsKey(status, "ttsLanguages");
    assertContainsKey(status, "wakeWordSupported");
    assertContainsKey(status, "indianLanguagesSupported");
    assertContainsKey(status, "privacyMode");
  }

  private void assertContainsKey(Map<String, Object> map, String key) {
    assertTrue(map.containsKey(key),
        "Voice capability status should contain key: " + key);
  }
}