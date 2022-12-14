package org.alkemy.wallet.repository;

import org.alkemy.wallet.model.Role;
import org.alkemy.wallet.model.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IRoleRepository extends JpaRepository<Role, Long> {

	Role findByRoleName(RoleName roleName);

}
