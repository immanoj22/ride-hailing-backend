package com.ridehail.userservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ridehail.userservice.entity.Address;

/** Backs service/user.services.js's createAddress/updateAddress/findOneAddress. */
@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByUserId(UUID userId);

    List<Address> findByUserIdAndDeletedFalse(UUID userId);

    /** Batch lookup backing getUsers()'s unconditional ".populate('Address')" for a page of users
     *  (admin-reporting-notifications bucket: get_users_list/get_users_details/get_user_notification/
     *  get_users_ride_report). */
    List<Address> findByUserIdIn(Collection<UUID> userIds);
}
