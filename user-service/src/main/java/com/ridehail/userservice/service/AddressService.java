package com.ridehail.userservice.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehail.common.constants.StatusCode;
import com.ridehail.common.exception.ApiException;
import com.ridehail.common.security.AuthContext;
import com.ridehail.userservice.dto.AddAddressRequest;
import com.ridehail.userservice.dto.AddressResponse;
import com.ridehail.userservice.dto.UpdateAddressRequest;
import com.ridehail.userservice.dto.UserResponse;
import com.ridehail.userservice.entity.Address;
import com.ridehail.userservice.entity.User;
import com.ridehail.userservice.entity.UserActivity;
import com.ridehail.userservice.repository.AddressRepository;
import com.ridehail.userservice.repository.UserActivityRepository;
import com.ridehail.userservice.repository.UserRepository;
import com.ridehail.userservice.util.RequestMetaUtil.RequestMeta;
import com.ridehail.userservice.util.UserActivityAction;

/**
 * "address-book" bucket - direct port of user.controller.js's add_address/update_address, backed
 * by service/user.services.js's createAddress/updateAddress/findOneAddress/findOneUser/updateUser
 * and helpers/user.commonFunction.js's logActivity.
 */
@Service
@Transactional(readOnly = true)
public class AddressService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final UserActivityRepository userActivityRepository;
    private final ObjectMapper objectMapper;

    public AddressService(
            UserRepository userRepository,
            AddressRepository addressRepository,
            UserActivityRepository userActivityRepository,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.userActivityRepository = userActivityRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /add-address. Mirrors add_address: the falsy-check on {@code coordinate.lat}/
     * {@code coordinate.lng} in the original ({@code if (coordinate?.lat && coordinate?.lng)}) is
     * preserved exactly, including the pre-existing quirk that a literal {@code 0} for either
     * value is treated as "no coordinate" (0 is falsy in JS) even though Joi already required both
     * as numbers.
     */
    @Transactional
    public Map<String, Object> addAddress(AddAddressRequest request, RequestMeta requestMeta) {
        UUID userId = currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "User not found"));

        Double lat = request.getCoordinate().getLat();
        Double lng = request.getCoordinate().getLng();
        boolean hasCoordinate = isTruthy(lat) && isTruthy(lng);

        Address address = Address.builder()
                .userId(user.getId())
                .label(request.getLabel())
                .street1(request.getStreet1())
                .street2(orEmpty(request.getStreet2()))
                .landmark(orEmpty(request.getLandmark()))
                .type(request.getType())
                .district(orEmpty(request.getDistrict()))
                .coordinateLat(hasCoordinate ? lat : null)
                .coordinateLng(hasCoordinate ? lng : null)
                .build();
        address = addressRepository.save(address);

        logActivity(user.getId(), UserActivityAction.ADD_ADDRESS, requestMeta, Map.of("addressId", address.getId().toString()));

        UserResponse userResponse = buildUserResponse(user);
        return Map.of("user", userResponse, "address", AddressResponse.from(address));
    }

    /**
     * POST /update-address. Mirrors update_address's single-function delete-or-edit branch
     * exactly, including the ownership check ({@code user.Address} array membership in the
     * original). Since the Foundation-step schema normalized {@code user.Address} into a plain
     * FK on the address row (no array of ids on the user row), "no longer referenced by the user"
     * is reproduced as "address.userId != userId OR the address was already soft-deleted" - the
     * original's {@code $pull} on delete removed the id from that array, so any later
     * update/delete attempt against an already-deleted address also fell through to the same 403
     * in the original.
     */
    @Transactional
    public UpdateAddressResult updateAddress(UpdateAddressRequest request, RequestMeta requestMeta) {
        UUID userId = currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "User not found"));

        UUID addressId = parseAddressId(request.getAddressId());
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ApiException(StatusCode.NOT_FOUND, "Address not found"));

        if (!userId.equals(address.getUserId()) || Boolean.TRUE.equals(address.getDeleted())) {
            throw new ApiException(StatusCode.FORBIDDEN, "Address does not belong to this user");
        }

        if ("delete".equals(request.getAction())) {
            address.setDeleted(true);
            addressRepository.save(address);

            logActivity(userId, UserActivityAction.DELETE_ADDRESS, requestMeta, Map.of("addressId", addressId.toString()));

            return new UpdateAddressResult("Address deleted successfully", buildUserResponse(user));
        }

        address.setLabel(request.getLabel());
        address.setStreet1(request.getStreet1());
        if (request.getStreet2() != null) {
            address.setStreet2(request.getStreet2());
        }
        if (request.getLandmark() != null) {
            address.setLandmark(request.getLandmark());
        }
        address.setType(request.getType());
        if (request.getDistrict() != null) {
            address.setDistrict(request.getDistrict());
        }
        address.setCoordinateLat(request.getCoordinate().getLat());
        address.setCoordinateLng(request.getCoordinate().getLng());
        addressRepository.save(address);

        logActivity(userId, UserActivityAction.EDIT_ADDRESS, requestMeta, Map.of("addressId", addressId.toString()));

        return new UpdateAddressResult("Address updated", Map.of("address", AddressResponse.from(address)));
    }

    private UserResponse buildUserResponse(User user) {
        List<AddressResponse> addresses = addressRepository.findByUserIdAndDeletedFalse(user.getId())
                .stream()
                .map(AddressResponse::from)
                .collect(Collectors.toList());
        return UserResponse.from(user, addresses);
    }

    private void logActivity(UUID userId, String action, RequestMeta requestMeta, Map<String, Object> meta) {
        String metaJson;
        try {
            metaJson = objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            metaJson = "{}";
        }
        UserActivity activity = UserActivity.builder()
                .userId(userId)
                .action(action)
                .ip(requestMeta.ip())
                .device(requestMeta.device())
                .meta(metaJson)
                .build();
        userActivityRepository.save(activity);
    }

    private UUID currentUserId() {
        AuthContext authContext = AuthContext.current();
        String principalId = authContext == null ? null : authContext.principalId();
        if (principalId == null) {
            throw new ApiException(StatusCode.UNAUTHORIZED, "Unauthorized");
        }
        try {
            return UUID.fromString(principalId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(StatusCode.UNAUTHORIZED, "Unauthorized");
        }
    }

    private UUID parseAddressId(String addressId) {
        try {
            return UUID.fromString(addressId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(StatusCode.NOT_FOUND, "Address not found");
        }
    }

    /** JS truthiness of a required Joi number: only {@code 0} is falsy (NaN can't occur here). */
    private boolean isTruthy(Double value) {
        return value != null && value != 0.0;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Internal transport for update_address's two differently-shaped success responses. */
    public record UpdateAddressResult(String message, Object data) {
    }
}
