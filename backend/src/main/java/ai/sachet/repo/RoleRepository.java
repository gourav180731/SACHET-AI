package ai.sachet.repo;

import ai.sachet.domain.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
  Optional<Role> findByName(String name);
}
