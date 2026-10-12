package ai.sachet.service;

import ai.sachet.config.SachetProperties;
import ai.sachet.security.SachetPrincipal;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Model Gateway factory - selects the appropriate provider based on configuration.
 */
@Service
public class ModelGatewayFactory {

  private final Map<String, ModelGateway> gateways;
  private final SachetProperties props;

  public ModelGatewayFactory(List<ModelGateway> gatewayList, SachetProperties props) {
    this.gateways = gatewayList.stream()
        .collect(Collectors.toMap(
            g -> g.getSupportedModels().get(0).toLowerCase(),
            g -> g));
    this.props = props;
  }

  public ModelGateway getGateway() {
    String model = props.getGeminiModel().toLowerCase();
    ModelGateway gateway = gateways.get(model);
    if (gateway == null || !gateway.isAvailable()) {
      // Fallback to any available gateway
      return gateways.values().stream()
          .filter(ModelGateway::isAvailable)
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("No model gateway available"));
    }
    return gateway;
  }

  public boolean isAnyAvailable() {
    return gateways.values().stream().anyMatch(ModelGateway::isAvailable);
  }
}