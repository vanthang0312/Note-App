package com.example.noteapp

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.noteapp.ui.theme.NoteAppTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore

class MainActivity : ComponentActivity() {

    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val noteRef = db.collection("notes")
    private val userRef = db.collection("users")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Khởi tạo Cloudinary
        try {
            val config = mapOf(
                "cloud_name" to "dmwwg6qrt",
                "api_key" to "487522645529365",
                "api_secret" to "r3dq-VBeMZxRgaNlndf_gVp1Qwg"
            )
            MediaManager.init(this, config)
        } catch (e: Exception) {
            // Đã khởi tạo hoặc lỗi khác
        }

        setContent {
            NoteAppTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthStateScreen()
                }
            }
        }
    }

    @Composable
    fun AuthStateScreen() {
        var user by remember { mutableStateOf(auth.currentUser) }

        DisposableEffect(auth) {
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                user = firebaseAuth.currentUser
            }
            auth.addAuthStateListener(listener)
            onDispose { auth.removeAuthStateListener(listener) }
        }

        Crossfade(targetState = user, label = "AuthTransition") { currentUser ->
            if (currentUser == null) {
                AuthScreen()
            } else {
                MainNoteScreen()
            }
        }
    }

    // ==================== AUTH SCREEN (BEAUTIFUL REDESIGN) ====================
    @Composable
    fun AuthScreen() {
        var isLogin by remember { mutableStateOf(true) }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Animated background gradient effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Application Logo/Icon
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(12.dp, CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxSize(),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (isLogin) "Welcome Back" else "Join NoteApp",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = if (isLogin) "Sign in to your account" else "Start organizing your life today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(40.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        if (isLogin) {
                            auth.signInWithEmailAndPassword(email, password)
                                .addOnCompleteListener { isLoading = false }
                        } else {
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val uid = auth.currentUser?.uid ?: ""
                                        val newUser = User(uid = uid, email = email, role = "user")
                                        userRef.document(uid).set(newUser).addOnCompleteListener {
                                            isLoading = false
                                            isLogin = true
                                        }
                                    } else {
                                        isLoading = false
                                        Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isLogin) "SIGN IN" else "CREATE ACCOUNT",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(onClick = { isLogin = !isLogin }) {
                    Text(
                        text = if (isLogin) "Don't have an account? Sign up" else "Already have an account? Sign in",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // ==================== MAIN NOTE SCREEN ====================
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainNoteScreen() {
        var notes by remember { mutableStateOf(listOf<Note>()) }
        var showDialog by remember { mutableStateOf(false) }
        var editingNote by remember { mutableStateOf<Note?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var userRole by remember { mutableStateOf("user") }

        LaunchedEffect(Unit) {
            auth.currentUser?.uid?.let { uid ->
                userRef.document(uid).get().addOnSuccessListener { doc ->
                    userRole = doc.getString("role") ?: "user"
                }
            }
            getNotes { 
                notes = it
                isLoading = false
            }
        }

        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = { 
                        Text(
                            "Book Note",
                            fontWeight = FontWeight.Black
                        ) 
                    },
                    actions = {
                        IconButton(
                            onClick = { auth.signOut() },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign Out",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                if (userRole == "admin") {
                    ExtendedFloatingActionButton(
                        onClick = { editingNote = null; showDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(20.dp),
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("New Note", fontWeight = FontWeight.Bold) }
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (notes.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NoteAdd,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Your thoughts belong here",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(notes) { note ->
                            NoteCard(
                                note = note,
                                isAdmin = userRole == "admin",
                                onEdit = { if (userRole == "admin") { editingNote = note; showDialog = true } },
                                onDelete = { if (userRole == "admin") deleteNote(note.id) { getNotes { notes = it } } },
                                onDownloadFile = { downloadMedia(note.fileUrl, note.fileName) },
                                onDownloadImage = { downloadMedia(note.imageUrl, "image_${note.id}.jpg") }
                            )
                        }
                    }
                }
            }
        }

        if (showDialog && userRole == "admin") {
            NoteDialog(
                note = editingNote,
                onDismiss = { showDialog = false; editingNote = null },
                onSave = { title, desc, imageUri, fileUri, fileName ->
                    if (editingNote == null) {
                        addNoteWithFile(title, desc, imageUri, fileUri, fileName) { getNotes { notes = it } }
                    } else {
                        updateNote(editingNote!!.id, title, desc, imageUri, fileUri, fileName) { getNotes { notes = it } }
                    }
                    showDialog = false
                    editingNote = null
                }
            )
        }
    }

    @Composable
    fun NoteCard(
        note: Note, 
        isAdmin: Boolean, 
        onEdit: () -> Unit, 
        onDelete: () -> Unit, 
        onDownloadFile: () -> Unit,
        onDownloadImage: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = isAdmin) { onEdit() },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = note.title.ifEmpty { "Untitled" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = note.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isAdmin) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                if (note.imageUrl.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = note.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                        // Nút tải ảnh
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(36.dp)
                                .clickable { onDownloadImage() },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download Image",
                                tint = Color.White,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                if (note.fileUrl.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDownloadFile() },
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = note.fileName.ifEmpty { "File" },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun NoteDialog(note: Note?, onDismiss: () -> Unit, onSave: (String, String, Uri?, Uri?, String?) -> Unit) {
        var title by remember { mutableStateOf(note?.title ?: "") }
        var description by remember { mutableStateOf(note?.description ?: "") }
        var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
        var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
        var selectedFileName by remember { mutableStateOf<String?>(null) }

        val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedImageUri = it }
        val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            selectedFileUri = uri
            selectedFileName = uri?.lastPathSegment?.substringAfterLast('/') ?: "file"
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(28.dp),
            title = { 
                Text(
                    text = if (note == null) "New Note" else "Edit Note",
                    fontWeight = FontWeight.Bold 
                ) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("Start writing...") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { imageLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Image", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { fileLauncher.launch("*/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("File", fontSize = 12.sp)
                        }
                    }

                    if (selectedImageUri != null) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (selectedFileName != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(selectedFileName!!, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSave(title, description, selectedImageUri, selectedFileUri, selectedFileName) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Note")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }

    private fun downloadMedia(url: String, fileName: String) {
        if (url.isEmpty()) return
        val request = DownloadManager.Request(url.toUri())
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show()
    }

    private fun addNoteWithFile(t: String, d: String, i: Uri?, f: Uri?, fn: String?, onSuccess: () -> Unit) {
        val data = hashMapOf("title" to t, "description" to d, "timestamp" to FieldValue.serverTimestamp())
        when {
            f != null -> uploadToCloudinary(f) { url ->
                data["fileUrl"] = url
                data["fileName"] = fn ?: "file"
                noteRef.add(data).addOnSuccessListener { onSuccess() }
            }
            i != null -> uploadToCloudinary(i) { url ->
                data["imageUrl"] = url
                noteRef.add(data).addOnSuccessListener { onSuccess() }
            }
            else -> noteRef.add(data).addOnSuccessListener { onSuccess() }
        }
    }

    private fun updateNote(id: String, t: String, d: String, i: Uri?, f: Uri?, fn: String?, onSuccess: () -> Unit) {
        val up = hashMapOf<String, Any>("title" to t, "description" to d)
        when {
            f != null -> uploadToCloudinary(f) { url ->
                up["fileUrl"] = url
                up["fileName"] = fn ?: "file"
                noteRef.document(id).update(up).addOnSuccessListener { onSuccess() }
            }
            i != null -> uploadToCloudinary(i) { url ->
                up["imageUrl"] = url
                noteRef.document(id).update(up).addOnSuccessListener { onSuccess() }
            }
            else -> noteRef.document(id).update(up).addOnSuccessListener { onSuccess() }
        }
    }

    private fun uploadToCloudinary(uri: Uri, onComplete: (String) -> Unit) {
        MediaManager.get().upload(uri).callback(object : UploadCallback {
            override fun onStart(requestId: String?) {}
            override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
            override fun onSuccess(requestId: String?, resultData: Map<*, *>) {
                val url = resultData["secure_url"].toString()
                onComplete(url)
            }
            override fun onError(requestId: String?, error: ErrorInfo?) {}
            override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
        }).dispatch()
    }

    private fun getNotes(callback: (List<Note>) -> Unit) {
        noteRef.get().addOnSuccessListener { result ->
            callback(result.map { doc ->
                Note(
                    id = doc.id,
                    title = doc.getString("title") ?: "",
                    description = doc.getString("description") ?: "",
                    imageUrl = doc.getString("imageUrl") ?: "",
                    fileUrl = doc.getString("fileUrl") ?: "",
                    fileName = doc.getString("fileName") ?: ""
                )
            })
        }
    }

    private fun deleteNote(id: String, onSuccess: () -> Unit) {
        noteRef.document(id).delete().addOnSuccessListener { onSuccess() }
    }
}