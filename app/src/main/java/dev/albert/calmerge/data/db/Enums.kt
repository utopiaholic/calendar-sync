package dev.albert.calmerge.data.db

enum class AccountType { MS, GOOGLE, ICS }

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
