# 👤 User-Specific Database Implementation

## Overview

This document explains how the fitness tracker database is now **user-specific**, ensuring each account has independent workout history and progress tracking.

---

## 🔧 Implementation Details

### **1. UserSessionManager Utility**

Created: `app/src/main/java/com/example/fitnesstracker/util/UserSessionManager.kt`

**Purpose:** Centralized helper to manage user session and ID conversion.

**Key Functions:**
```kotlin
// Get current Firebase user ID
fun getCurrentFirebaseUserId(): String?

// Get user ID from DataStore (cached)
suspend fun getUserIdFromDataStore(dataStore: DataStore<Preferences>): String?

// Get current user ID (tries Firebase first, then DataStore)
suspend fun getCurrentUserId(dataStore: DataStore<Preferences>): String?

// Convert Firebase UID (String) to Room database ID (Long)
// Uses deterministic hashCode for consistency
fun getUserIdAsLong(firebaseUid: String?): Long?

// Check if user is logged in
fun isUserLoggedIn(): Boolean
```

**Why Conversion?**
- Firebase uses `String` UIDs (e.g., "abc123xyz")
- Room database uses `Long` for foreign keys
- Conversion is deterministic: same UID always produces same Long

---

### **2. Database Schema**

**WorkoutSession Entity:**
```kotlin
@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE // Delete sessions when user is deleted
        )
    ],
    indices = [Index(value = ["userId"])] // Index for fast filtering
)
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long? = null, // ✅ Links workout to specific user
    val workoutType: String,
    val date: Date,
    val totalReps: Int,
    // ... other fields
)
```

**WorkoutSessionDao:**
```kotlin
@Dao
interface WorkoutSessionDao {
    // ✅ User-specific queries
    @Query("SELECT * FROM workout_sessions WHERE userId = :userId ORDER BY date DESC")
    fun getSessionsByUser(userId: Long): Flow<List<WorkoutSession>>
    
    @Query("SELECT COUNT(*) FROM workout_sessions WHERE userId = :userId")
    suspend fun getTotalSessionsByUser(userId: Long): Int
    
    @Query("SELECT SUM(totalReps) FROM workout_sessions WHERE userId = :userId")
    suspend fun getTotalRepsByUser(userId: Long): Int?
    
    @Query("SELECT AVG(formScore) FROM workout_sessions WHERE userId = :userId")
    suspend fun getAverageFormScoreByUser(userId: Long): Float?
}
```

---

### **3. WorkoutViewModel Changes**

**Updated Constructor:**
```kotlin
class WorkoutViewModel(
    private val dao: WorkoutSessionDao,
    private val dataStore: DataStore<Preferences> // ✅ Added for user ID access
) : ViewModel()
```

**Save Workout with User ID:**
```kotlin
fun stopWorkout() {
    viewModelScope.launch {
        // ✅ Get current user ID
        val firebaseUid = UserSessionManager.getCurrentUserId(dataStore)
        val userId = UserSessionManager.getUserIdAsLong(firebaseUid)
        
        val session = WorkoutSession(
            userId = userId, // ✅ CRITICAL: Associate with current user
            workoutType = _selectedWorkoutType.value ?: "Push-Ups",
            date = Date(),
            totalReps = reps,
            // ... other fields
        )
        
        dao.insertSession(session)
    }
}
```

---

### **4. HomeScreen User Filtering**

**Before:**
```kotlin
// ❌ Shows ALL workouts from ALL users
database.workoutSessionDao().getAllSessions().map { it.size }
```

**After:**
```kotlin
// ✅ Only shows CURRENT USER's workouts
val userId by remember {
    flow {
        val firebaseUid = UserSessionManager.getCurrentUserId(context.dataStore)
        emit(UserSessionManager.getUserIdAsLong(firebaseUid))
    }
}.collectAsStateWithLifecycle(initialValue = null)

database.workoutSessionDao().getSessionsByUser(userId!!).map { it.size }
```

**Stats Now User-Specific:**
- Workout count
- Total reps
- Average form score

---

### **5. HistoryScreen User Filtering**

**Before:**
```kotlin
// ❌ Shows ALL sessions from ALL users
database.workoutSessionDao().getAllSessions()
```

**After:**
```kotlin
// ✅ Only shows CURRENT USER's sessions
val userId by remember {
    flow {
        val firebaseUid = UserSessionManager.getCurrentUserId(context.dataStore)
        emit(UserSessionManager.getUserIdAsLong(firebaseUid))
    }
}.collectAsStateWithLifecycle(initialValue = null)

database.workoutSessionDao().getSessionsByUser(userId!!)
```

---

## 🔐 How It Works

### **User Login Flow:**

1. **User logs in via Firebase Auth**
   ```kotlin
   FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
   ```

2. **Firebase UID is stored in DataStore**
   ```kotlin
   dataStore.edit { preferences ->
       preferences[USER_ID_KEY] = firebaseUser.uid
   }
   ```

3. **On workout save:**
   - Get Firebase UID from DataStore
   - Convert to Long using `getUserIdAsLong()`
   - Save workout with `userId` field

4. **On data query:**
   - Get current user's Long ID
   - Query database with `getSessionsByUser(userId)`
   - Display only that user's data

---

## 🎯 Benefits

### **Data Isolation:**
- ✅ Each user sees ONLY their own workouts
- ✅ User A cannot see User B's data
- ✅ Progress tracking is independent per account

### **Multi-User Support:**
- ✅ Multiple accounts can use the same device
- ✅ Switching accounts shows different histories
- ✅ Data persists across app restarts

### **Security:**
- ✅ Foreign key constraints ensure data integrity
- ✅ CASCADE delete removes user's data when account deleted
- ✅ Indexed userId for fast queries

---

## 📊 Database Relationships

```
User (Firebase Auth)
  ├─> Firebase UID: "abc123xyz" (String)
  ├─> Room userId: 1234567890 (Long, from hashCode)
  └─> WorkoutSessions (Multiple)
       ├─> WorkoutSession #1 (userId: 1234567890)
       ├─> WorkoutSession #2 (userId: 1234567890)
       └─> WorkoutSession #3 (userId: 1234567890)

Different User
  ├─> Firebase UID: "def456uvw" (String)
  ├─> Room userId: 9876543210 (Long, from hashCode)
  └─> WorkoutSessions (Multiple)
       ├─> WorkoutSession #1 (userId: 9876543210)
       └─> WorkoutSession #2 (userId: 9876543210)
```

---

## 🧪 Testing Scenarios

### **Test 1: Single User**
1. User A logs in
2. Completes 5 workouts
3. HomeScreen shows: 5 workouts
4. HistoryScreen shows: 5 sessions

### **Test 2: Multiple Users**
1. User A logs in, completes 5 workouts
2. User A logs out
3. User B logs in, completes 3 workouts
4. User B sees: 3 workouts (NOT 8!)
5. User A logs back in, sees: 5 workouts (unchanged)

### **Test 3: New User**
1. User C creates new account
2. HomeScreen shows: 0 workouts
3. Empty state displayed in HistoryScreen
4. After first workout, stats update correctly

---

## 🚀 Migration from Old Data

If you have existing workouts without `userId`:

**Option 1: Assign to Current User (Recommended)**
```kotlin
// Run once on app start
viewModelScope.launch {
    val currentUserId = UserSessionManager.getUserIdAsLong(
        UserSessionManager.getCurrentUserId(dataStore)
    )
    
    // Find all sessions without userId
    val orphanedSessions = dao.getAllSessions().first()
        .filter { it.userId == null }
    
    // Assign to current user
    orphanedSessions.forEach { session ->
        dao.updateSession(session.copy(userId = currentUserId))
    }
}
```

**Option 2: Delete Old Data**
```kotlin
// Clear database on version upgrade
@Migration(from = 2, to = 3)
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DELETE FROM workout_sessions WHERE userId IS NULL")
    }
}
```

---

## 📝 Modified Files

1. ✅ `util/UserSessionManager.kt` - New helper class
2. ✅ `viewmodel/WorkoutViewModel.kt` - Save with userId
3. ✅ `viewmodel/ViewModelFactory.kt` - Pass dataStore to ViewModel
4. ✅ `ui/screens/HomeScreen.kt` - Filter stats by user
5. ✅ `ui/screens/HistoryScreen.kt` - Filter sessions by user

---

## 🎉 Result

**Each user account now has:**
- ✅ Independent workout history
- ✅ Separate progress tracking
- ✅ Personalized statistics
- ✅ Data privacy and isolation

**Perfect for:**
- 🏠 Family sharing one device
- 👥 Gym with multiple trainers
- 📱 Multiple accounts per person (personal/work)

