package com.example.musicapp.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleAuthManager {
    suspend fun signIn(activity: Activity): UserProfile {
        val clientIdResource = activity.resources.getIdentifier(
            "default_web_client_id", "string", activity.packageName
        )
        val webClientId = clientIdResource.takeIf { it != 0 }?.let(activity::getString).orEmpty()
        check(webClientId.isNotBlank()) {
            "Google sign-in is not enabled in Firebase yet. Enable the Google provider, then download the updated google-services.json."
        }

        val googleOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()
        val response = CredentialManager.create(activity).getCredential(activity, request)
        val credential = response.credential as? CustomCredential
            ?: error("Google returned an unsupported sign-in response.")
        check(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google returned an unsupported sign-in response. Please try again."
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        val firebaseUser = withContext(Dispatchers.IO) {
            Tasks.await(FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)).user
        } ?: error("Firebase could not verify this Google account. Please try again.")

        return UserManager.getInstance(activity.applicationContext).loginWithGoogle(
            uid = firebaseUser.uid,
            email = firebaseUser.email.orEmpty(),
            displayName = firebaseUser.displayName.orEmpty()
        )
    }

    suspend fun signOut(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching { FirebaseAuth.getInstance().signOut() }
            runCatching {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }
}
