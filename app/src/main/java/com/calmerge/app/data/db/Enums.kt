package com.calmerge.app.data.db

/** ICS-only build: OAuth providers were removed after tenant consent was blocked. */
enum class AccountType { ICS }

enum class AccountStatus {
    ACTIVE,

    /** Token expired or revoked; user must re-authenticate (FR-4). Never a crash. */
    NEEDS_REAUTH,

    /** Last sync failed for a non-auth reason; message in [AccountEntity.lastSyncError]. */
    ERROR,
}

/** Normalized show-as/transparency across providers (FR-12). */
enum class ShowAs { BUSY, FREE, TENTATIVE, OOF, UNKNOWN }

/** Needed to exclude declined events from conflict detection (spec §8 case 4). */
enum class ResponseStatus { ACCEPTED, DECLINED, TENTATIVE, NOT_RESPONDED, ORGANIZER, UNKNOWN }
