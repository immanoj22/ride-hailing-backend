package com.ridehail.driverservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.driverservice.entity.DriverWallet;

public interface DriverWalletRepository extends JpaRepository<DriverWallet, UUID> {

    Optional<DriverWallet> findByDriver_Id(UUID driverId);
}
