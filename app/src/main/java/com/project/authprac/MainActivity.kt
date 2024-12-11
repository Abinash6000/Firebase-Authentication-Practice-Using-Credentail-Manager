package com.project.authprac

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.project.authprac.ui.theme.AuthPracTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.security.MessageDigest
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuthPracTheme {
                LoginScreen()
            }
        }
    }
}

@Composable
fun LoginScreen() {

    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    val context = LocalContext.current

    val authenticationManager = remember {
        AuthenticationManager(context)
    }

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sign-in",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Please fill the form to continue",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { newValue ->
                email = newValue
            },
            placeholder = {
                Text(text = "Email")
            },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Email, contentDescription = null)
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { newValue ->
                password = newValue
            },
            placeholder = {
                Text(text = "Password")
            },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Lock, contentDescription = null)
            },
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                authenticationManager.loginWithEmail(email, password)
                    .onEach { response ->
                        if(response is AuthResponse.Success) {
                            Toast.makeText(context, "Success", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Error: ${response.toString()}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .launchIn(coroutineScope)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Sign-in",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "or continue with")
        }

        OutlinedButton(
            onClick = {
                authenticationManager.signInWithGoogle()
                    .onEach { response ->
                        if(response is AuthResponse.Success) {
                            Toast.makeText(context, "Success", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .launchIn(coroutineScope)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.google),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "Sign-in with Google",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    AuthPracTheme {
        LoginScreen()
    }
}

class AuthenticationManager(val context: Context) {
    // wee need an auth variable
    private val auth = Firebase.auth // auth: An instance of Firebase Authentication.

    // create account with email function
    // Returns a Flow (a Kotlin coroutine-based data stream) of type AuthResponse
    fun createAccountWithEmail(email: String, password: String): Flow<AuthResponse> = callbackFlow { // callbackFlow: A special coroutine builder that bridges callback-based APIs to flows.
        auth.createUserWithEmailAndPassword(email, password) // Firebase method to create a new account with the provided email and password.
            .addOnCompleteListener { task -> // Adds a listener to handle the completion of the Firebase account creation task.
                if(task.isSuccessful) { // Check if account creation was successful
                    trySend(AuthResponse.Success) // Safely emits a value to the `Flow`. Sends a success response (e.g., AuthResponse.Success)
                } else {
                    trySend(AuthResponse.Error(message = task.exception?.message ?: "")) // Sends an error response (AuthResponse.Error), with the failure message.
                }
            }

        awaitClose() // Ensures that any resources (like listeners) used in the callbackFlow are cleaned up when the flow collector is no longer active.
    }

    // to authenticate an user
    fun loginWithEmail(email: String, password: String): Flow<AuthResponse> = callbackFlow { // callbackFlow: A special coroutine builder that bridges callback-based APIs to flows.
        auth.signInWithEmailAndPassword(email, password) // Firebase method to authenticate a user with an email and password.
            .addOnCompleteListener { task -> // Handles the completion of the authentication task
                if(task.isSuccessful) {
                    trySend(AuthResponse.Success) // If the login is successful, emits AuthResponse.Success
                } else {
                    trySend(AuthResponse.Error(message = task.exception?.message ?: "")) // Sends an error response if the login fails, including the exception message.
                }
            }

        awaitClose() // Ensures cleanup
    }

    // nonce to create google sign in request
    private fun createNonce(): String { // Returns a string, used as a nonce (a unique random value)
        val rawNonce = UUID.randomUUID().toString() // Generates a universally unique identifier (UUID).
        val bytes = rawNonce.toByteArray() // Converts the string rawNonce into an array of bytes.
        val md = MessageDigest.getInstance("SHA-256") // Creates a MessageDigest instance for generating a SHA-256 hash.
        val digest = md.digest(bytes) // Computes the SHA-256 hash of the byte array.

        return digest.fold("") { str, it -> // Iteratively processes each byte of the hash, starting with an empty string.
            str + "%02x".format(it) // Formats each byte as a two-character hexadecimal string.
        }
    }

    fun signInWithGoogle(): Flow<AuthResponse> = callbackFlow { // callbackFlow: Used to bridge callback-based APIs to Flow.
        val googleIdOption = GetGoogleIdOption.Builder() // Builder for Google ID options.
            .setFilterByAuthorizedAccounts(false) // Do not filter by previously authorized accounts. This allows users to select any Google account, whether or not it has been used with the app in the past.
            .setServerClientId(context.getString(R.string.web_client_id)) // Sets the server client ID (usually from Firebase configuration).
            .setAutoSelectEnabled(false) // Disables auto-selection of accounts.
            .setNonce(createNonce()) // Adds a unique, SHA-256-hashed nonce for security.
            .build() // Builds the Google ID option.

        val request = GetCredentialRequest.Builder() // Builds a request to get credentials.
            .addCredentialOption(googleIdOption) // Adds the Google ID option to the request.
            .build() // Constructs the credential request.

        try {
            val credentialManager = CredentialManager.create(context) // Creates an instance of the credential manager.
            val result = credentialManager.getCredential( // Retrieves credentials based on the request.
                context = context,
                request = request
            )

            val credential = result.credential // Extracts the retrieved credential.
            if(credential is CustomCredential) { // Checks if the credential is of type CustomCredential.
                if(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { // Ensures it’s a Google ID token.
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential
                            .createFrom(credential.data) // Parses the raw credential data into a Google ID token credential.

                        val firebaseCredential = GoogleAuthProvider
                            .getCredential(
                                googleIdTokenCredential.idToken, // Converts the Google ID token into a Firebase credential.
                                null
                            )

                        auth.signInWithCredential(firebaseCredential) // Authenticates the user in Firebase using the Google credential.
                            .addOnCompleteListener { // Handles the completion of the Firebase sign-in task.
                                if(it.isSuccessful) { // Sends success or error responses to the Flow.
                                    trySend(AuthResponse.Success)
                                } else {
                                    trySend(AuthResponse.Error(message = it.exception?.message ?: ""))
                                }
                            }

                    } catch (e: GoogleIdTokenParsingException) { // Handles errors during Google ID token parsing.
                        trySend(AuthResponse.Error(message = e.message ?: ""))
                    }
                }
            }
        } catch (e: Exception) { // Handles any other exceptions during the process.
            trySend(AuthResponse.Error(message = e.message ?: ""))
        }

        awaitClose() // Ensures proper cleanup when the Flow collector is no longer active.
    }
}

// interface representing response of authentication operation
interface AuthResponse {
    // it's a singleton
    data object Success: AuthResponse
    // works like any other class which extends
    data class Error(val message: String): AuthResponse
}