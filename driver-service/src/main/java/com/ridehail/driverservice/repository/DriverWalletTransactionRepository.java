package com.ridehail.driverservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ridehail.driverservice.entity.DriverWalletTransaction;

public interface DriverWalletTransactionRepository extends JpaRepository<DriverWalletTransaction, UUID> {

    List<DriverWalletTransaction> findByDriver_Id(UUID driverId);

    Optional<DriverWalletTransaction> findByDriver_IdAndTransactionTypeAndStatus(
            UUID driverId, String transactionType, String status);

    List<DriverWalletTransaction> findByDriver_IdAndTransactionType(UUID driverId, String transactionType);

    Page<DriverWalletTransaction> findByTransactionType(String transactionType, Pageable pageable);

    Page<DriverWalletTransaction> findByTransactionTypeAndStatus(
            String transactionType, String status, Pageable pageable);

    Page<DriverWalletTransaction> findByTransactionTypeAndDriver_Id(
            String transactionType, UUID driverId, Pageable pageable);

    Page<DriverWalletTransaction> findByTransactionTypeAndStatusAndDriver_Id(
            String transactionType, String status, UUID driverId, Pageable pageable);

    long countByTransactionType(String transactionType);

    long countByTransactionTypeAndStatus(String transactionType, String status);

    long countByTransactionTypeAndDriver_Id(String transactionType, UUID driverId);

    long countByTransactionTypeAndStatusAndDriver_Id(String transactionType, String status, UUID driverId);

    /**
     * Port of listWalletClaims' summaryAgg aggregation pipeline (group by status, sum
     * requestedAmount/paidAmount), scoped to the same query as the paged listing (transactionType
     * + optional status), used only when no driverId filter is present.
     */
    @Query("""
            select t.status, count(t), coalesce(sum(t.requestedAmount), 0), coalesce(sum(t.paidAmount), 0)
            from DriverWalletTransaction t
            where t.transactionType = :transactionType
              and (:status is null or t.status = :status)
            group by t.status
            """)
    List<Object[]> aggregateByStatus(@Param("transactionType") String transactionType, @Param("status") String status);
}
