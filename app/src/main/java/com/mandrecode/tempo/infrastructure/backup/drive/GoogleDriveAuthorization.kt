package com.mandrecode.tempo.infrastructure.backup.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.repository.CloudAuthorization
import com.mandrecode.tempo.features.backup.domain.repository.CloudAuthorizationResult
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Drive access through Play services' AuthorizationClient (#304), for the `drive.appdata` scope
 * only. Silent by design: once granted, asking again returns a fresh access token (about an hour)
 * with no UI, which is all a scheduled backup needs. No refresh token is ever stored on the device.
 */
class GoogleDriveAuthorization
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CloudAuthorization {
        override suspend fun authorize(): CloudAuthorizationResult {
            val result = request()
            val token = result.accessToken
            return if (result.hasResolution() || token == null) {
                CloudAuthorizationResult.NeedsUserConsent
            } else {
                CloudAuthorizationResult.Authorized(token)
            }
        }

        /**
         * For the screen asking interactively: the consent to show, or null when access is
         * already granted. Throws [CloudBackupException] when Play services cannot authorise at all.
         */
        suspend fun consentIntent(): PendingIntent? = request().takeIf { it.hasResolution() }?.pendingIntent

        /**
         * How the consent screen launched from [consentIntent] ended. Backing out is [ConsentOutcome.CANCELLED];
         * anything else that didn't grant access is [ConsentOutcome.FAILED], so the screen can say so
         * instead of looking as if nothing happened. That includes an app with no OAuth client
         * registered for its package and signature (UNREGISTERED_ON_API_CONSOLE), which fails only
         * after an account is picked.
         */
        fun consentOutcome(data: Intent?): ConsentOutcome {
            if (data == null) return ConsentOutcome.CANCELLED
            return try {
                val token = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
                if (token != null) ConsentOutcome.GRANTED else ConsentOutcome.FAILED
            } catch (e: ApiException) {
                if (e.statusCode == CommonStatusCodes.CANCELED) ConsentOutcome.CANCELLED else ConsentOutcome.FAILED
            }
        }

        private suspend fun request(): AuthorizationResult =
            try {
                Identity
                    .getAuthorizationClient(context)
                    .authorize(AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_APPDATA))).build())
                    .await()
            } catch (e: ApiException) {
                val failure =
                    if (e.statusCode == CommonStatusCodes.NETWORK_ERROR) {
                        DriveBackupFailure.NETWORK
                    } else {
                        // DEVELOPER_ERROR (no OAuth client for this app and signature) and the like.
                        DriveBackupFailure.UNEXPECTED
                    }
                throw CloudBackupException(failure, e)
            }

        enum class ConsentOutcome { GRANTED, CANCELLED, FAILED }

        companion object {
            const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        }
    }
