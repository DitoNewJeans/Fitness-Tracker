package com.example.fitnesstracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.viewModelScope
import com.example.fitnesstracker.data.FitnessRepository
import com.example.fitnesstracker.data.firebase.FirebaseAuthService
import com.example.fitnesstracker.data.firebase.FirestoreRepository
import com.example.fitnesstracker.data.firebase.FirestoreUser
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FirebaseAuthViewModel(
    private val authService: FirebaseAuthService,
    private val firestoreRepository: FirestoreRepository,
    private val fitnessRepository: FitnessRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {
    
    private val _isFirstTimeUser = MutableStateFlow(true)
    val isFirstTimeUser: StateFlow<Boolean> = _isFirstTimeUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _signupError = MutableStateFlow<String?>(null)
    val signupError: StateFlow<String?> = _signupError.asStateFlow()

    init {
        checkFirstTimeUser()
        checkLoginStatus()
        initializeFirestore()
    }

    private fun initializeFirestore() {
        viewModelScope.launch {
            try {
                firestoreRepository.initializeDefaultExercises()
            } catch (e: Exception) {
                // Log error but don't crash - Firestore initialization is non-critical
                android.util.Log.e("FirebaseAuthViewModel", "Failed to initialize Firestore exercises: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun checkFirstTimeUser() {
        viewModelScope.launch {
            try {
                val preferences = dataStore.data.first()
                _isFirstTimeUser.value = preferences[FIRST_TIME_KEY] == null
            } catch (e: Exception) {
                android.util.Log.e("FirebaseAuthViewModel", "Failed to check first time user: ${e.message}")
                e.printStackTrace()
                // Default to first time user on error
                _isFirstTimeUser.value = true
            }
        }
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            try {
                // Firebase Auth automatically persists the user session
                // So currentUser should be available immediately if user was logged in
                val user = authService.currentUser
                if (user != null) {
                    _currentUser.value = user
                    _isLoggedIn.value = true
                    
                    // Create or get Room User for this Firebase user
                    val roomUser = fitnessRepository.getOrCreateUserFromFirebaseUid(
                        firebaseUid = user.uid,
                        name = user.displayName ?: "User",
                        email = user.email ?: ""
                    )
                    
                    // Ensure user data is saved to DataStore for UserSessionManager compatibility
                    try {
                        dataStore.edit { preferences ->
                            preferences[USER_ID_KEY] = user.uid
                            preferences[USER_NAME_KEY] = user.displayName ?: ""
                            preferences[USER_EMAIL_KEY] = user.email ?: ""
                            // Store Room User ID for quick access
                            preferences[ROOM_USER_ID_KEY] = roomUser.id.toString()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FirebaseAuthViewModel", "Failed to save user data to DataStore: ${e.message}")
                        e.printStackTrace()
                        // Continue - user is still logged in via Firebase Auth
                    }
                    
                    // Update last login in Firestore (non-blocking)
                    firestoreRepository.updateLastLogin(user.uid)
                } else {
                    // No user in Firebase Auth, clear DataStore as well
                    _currentUser.value = null
                    _isLoggedIn.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // On error, assume not logged in
                _currentUser.value = null
                _isLoggedIn.value = false
            }
        }
    }

    suspend fun createAccount(name: String, email: String, password: String): Boolean {
        return try {
            _signupError.value = null
            
            // Create Firebase Auth user
            val result = authService.signUp(email, password, name)
            
            if (result.isSuccess) {
                val firebaseUser = result.getOrNull()
                if (firebaseUser == null) {
                    _signupError.value = "User creation failed"
                    return false
                }
                
                // Create user document in Firestore
                val firestoreUser = FirestoreUser(
                    id = firebaseUser.uid,
                    name = name,
                    email = email,
                    createdAt = Timestamp.now()
                )
                
                val createResult = firestoreRepository.createUser(firestoreUser)
                
                if (createResult.isSuccess) {
                    // Create Room User for this Firebase user
                    val roomUser = fitnessRepository.getOrCreateUserFromFirebaseUid(
                        firebaseUid = firebaseUser.uid,
                        name = name,
                        email = email
                    )
                    
                    // Save to DataStore
                    try {
                        dataStore.edit { preferences ->
                            preferences[USER_ID_KEY] = firebaseUser.uid
                            preferences[USER_NAME_KEY] = name
                            preferences[USER_EMAIL_KEY] = email
                            preferences[FIRST_TIME_KEY] = "false"
                            // Store Room User ID for quick access
                            preferences[ROOM_USER_ID_KEY] = roomUser.id.toString()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FirebaseAuthViewModel", "Failed to save user data to DataStore during signup: ${e.message}")
                        e.printStackTrace()
                        // Continue - user is still created in Firebase
                    }
                    
                    _currentUser.value = firebaseUser
                    _isLoggedIn.value = true
                    true
                } else {
                    _signupError.value = "Failed to create user profile"
                    false
                }
            } else {
                val error = result.exceptionOrNull()
                _signupError.value = when {
                    error?.message?.contains("email address is already in use") == true -> 
                        "Email already registered"
                    error?.message?.contains("password") == true -> 
                        "Password should be at least 6 characters"
                    error?.message?.contains("email") == true -> 
                        "Invalid email address"
                    else -> "Failed to create account: ${error?.message}"
                }
                false
            }
        } catch (e: Exception) {
            _signupError.value = "Failed to create account: ${e.message}"
            false
        }
    }

    suspend fun login(email: String, password: String): Boolean {
        return try {
            _loginError.value = null
            
            val result = authService.signIn(email, password)
            
            if (result.isSuccess) {
                val firebaseUser = result.getOrNull()
                if (firebaseUser == null) {
                    _loginError.value = "Login failed: User not found"
                    return false
                }
                
                // Create or get Room User for this Firebase user
                val roomUser = fitnessRepository.getOrCreateUserFromFirebaseUid(
                    firebaseUid = firebaseUser.uid,
                    name = firebaseUser.displayName ?: "User",
                    email = firebaseUser.email ?: ""
                )
                
                // Update last login
                firestoreRepository.updateLastLogin(firebaseUser.uid)
                
                // Save to DataStore
                try {
                    dataStore.edit { preferences ->
                        preferences[USER_ID_KEY] = firebaseUser.uid
                        preferences[USER_NAME_KEY] = firebaseUser.displayName ?: ""
                        preferences[USER_EMAIL_KEY] = firebaseUser.email ?: ""
                        // Store Room User ID for quick access
                        preferences[ROOM_USER_ID_KEY] = roomUser.id.toString()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FirebaseAuthViewModel", "Failed to save user data to DataStore during login: ${e.message}")
                    e.printStackTrace()
                    // Continue - user is still logged in via Firebase Auth
                }
                
                _currentUser.value = firebaseUser
                _isLoggedIn.value = true
                true
            } else {
                val error = result.exceptionOrNull()
                _loginError.value = when {
                    error?.message?.contains("no user record") == true ||
                    error?.message?.contains("password is invalid") == true ||
                    error?.message?.contains("INVALID_LOGIN_CREDENTIALS") == true -> 
                        "Invalid email or password"
                    error?.message?.contains("network") == true -> 
                        "Network error. Please check your connection"
                    else -> "Login failed: ${error?.message}"
                }
                false
            }
        } catch (e: Exception) {
            _loginError.value = "Login failed: ${e.message}"
            false
        }
    }

    fun logout() {
        viewModelScope.launch {
            authService.signOut()
            try {
                dataStore.edit { preferences ->
                    preferences.remove(USER_ID_KEY)
                    preferences.remove(USER_NAME_KEY)
                    preferences.remove(USER_EMAIL_KEY)
                    preferences.remove(ROOM_USER_ID_KEY) // Clear Room User ID
                }
            } catch (e: Exception) {
                android.util.Log.e("FirebaseAuthViewModel", "Failed to clear user data from DataStore: ${e.message}")
                e.printStackTrace()
                // Continue - user is still logged out via Firebase Auth
            }
            _currentUser.value = null
            _isLoggedIn.value = false
        }
    }

    fun setFirstTimeComplete() {
        viewModelScope.launch {
            try {
                dataStore.edit { preferences ->
                    preferences[FIRST_TIME_KEY] = "false"
                }
            } catch (e: Exception) {
                android.util.Log.e("FirebaseAuthViewModel", "Failed to set first time complete: ${e.message}")
                e.printStackTrace()
                // Non-critical - continue
            }
        }
    }

    fun clearErrors() {
        _loginError.value = null
        _signupError.value = null
    }

    companion object {
        val FIRST_TIME_KEY = stringPreferencesKey("first_time_user")
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val USER_NAME_KEY = stringPreferencesKey("user_name")
        val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        val ROOM_USER_ID_KEY = stringPreferencesKey("room_user_id")
    }
}


