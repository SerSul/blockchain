package ru.vkr.blockchain.repository.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.vkr.blockchain.domain.entity.Authority;

import java.util.List;

@Repository
public interface AuthorityRepository extends JpaRepository<Authority, String> {
    List<Authority> findByIsActive(Boolean isActive);
}
