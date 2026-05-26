package com.nyxn.ecommerce.infrastructure.persistence.repository;

import com.nyxn.ecommerce.infrastructure.persistence.entity.ProductEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Spring Data JPA repository — adaptador de infraestructura. Solo vive en este paquete. */
public interface JpaProductRepository
    extends JpaRepository<ProductEntity, UUID>, JpaSpecificationExecutor<ProductEntity> {}
