package com.nyxn.ecommerce.infrastructure.persistence.repository;

import com.nyxn.ecommerce.infrastructure.persistence.entity.ProductEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Spring Data JPA repository. Stays inside the infrastructure layer — never referenced by domain or
 * application code.
 */
public interface JpaProductRepository
    extends JpaRepository<ProductEntity, UUID>, JpaSpecificationExecutor<ProductEntity> {}
