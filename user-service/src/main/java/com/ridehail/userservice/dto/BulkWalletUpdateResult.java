package com.ridehail.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Stand-in for the MongoDB driver's {@code BulkWriteResult} object that the original
 * update_user_wallet_balance returned verbatim as response data. Only the fields any caller could
 * meaningfully rely on are reproduced; insertedCount/upsertedCount/deletedCount are always 0 here
 * since this endpoint only ever issues updateOne ops.
 */
@Getter
@Setter
@AllArgsConstructor
public class BulkWalletUpdateResult {

    private long matchedCount;

    private long modifiedCount;

    private long insertedCount;

    private long upsertedCount;

    private long deletedCount;

    private boolean ok;
}
