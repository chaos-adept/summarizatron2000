package org.chaosadept.summaryzatron2000.repository;

import org.chaosadept.summaryzatron2000.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
