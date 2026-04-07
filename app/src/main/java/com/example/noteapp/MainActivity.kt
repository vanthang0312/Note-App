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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.distinctUntilChanged  // nếu dùng
import androidx.compose.foundation.BorderStroke
import java.io.File

class MainActivity : ComponentActivity() {

    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val noteRef = db.collection("notes")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Khởi tạo Cloudinary
        val config = mapOf(
            "cloud_name" to "dmwwg6qrt",
            "api_key" to "487522645529365",
            "api_secret" to "r3dq-VBeMZxRgaNlndf_gVp1Qwg"
        )
        try {
            MediaManager.init(this, config)
        } catch (e: Exception) {}

        setContent {
            NoteAppTheme(darkTheme = true) {
                AuthStateScreen()
            }
        }
    }

    // ==================== AUTH STATE ====================
    @Composable
    fun AuthStateScreen() {
        var user by remember { mutableStateOf(auth.currentUser) }

        DisposableEffect(auth) {
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                user = firebaseAuth.currentUser
            }
            auth.addAuthStateListener(listener)
            onDispose {
                auth.removeAuthStateListener(listener)
            }
        }

        if (user == null) {
            AuthScreen()
        } else {
            MainNoteScreen()
        }
    }

    // ==================== MÀN HÌNH ĐĂNG NHẬP / ĐĂNG KÝ ====================
    @Composable
    fun AuthScreen() {
        var isLogin by remember { mutableStateOf(true) }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isLogin) "Đăng nhập" else "Đăng ký",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mật khẩu") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true

                    if (isLogin) {
                        // Đăng nhập
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (!task.isSuccessful) {
                                    Toast.makeText(context, "Đăng nhập thất bại. Kiểm tra lại email/mật khẩu!", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        // Đăng ký
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    Toast.makeText(context, "Đăng ký thành công! Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show()
                                    isLogin = true
                                } else {
                                    Toast.makeText(context, "Đăng ký thất bại: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Text(if (isLogin) "Đăng nhập" else "Đăng ký")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { isLogin = !isLogin }) {
                Text(
                    text = if (isLogin) "Chưa có tài khoản? Đăng ký" else "Đã có tài khoản? Đăng nhập",
                    color = Color(0xFF64B5F6)
                )
            }
        }
    }

    // ==================== MAIN NOTE SCREEN ====================
    @Composable
    fun MainNoteScreen() {
        var notes by remember { mutableStateOf(listOf<Note>()) }
        var showDialog by remember { mutableStateOf(false) }
        var editingNote by remember { mutableStateOf<Note?>(null) }

        LaunchedEffect(Unit) {
            getNotes { notes = it }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            // ==================== TOP BAR ====================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF111111),           // Nền tối hơn một chút
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Notes",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // NÚT ĐĂNG XUẤT ĐÃ CÓ BO VIỀN ĐẸP
                    OutlinedButton(
                        onClick = { auth.signOut() },
                        shape = RoundedCornerShape(20.dp),           // Bo viền tròn mềm
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), // Viền tím neon
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Đăng xuất",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notes) { note ->
                    NoteCard(
                        note = note,
                        onEdit = { editingNote = note; showDialog = true },
                        onDelete = { deleteNote(note.id) { getNotes { notes = it } } },
                        onDownload = { downloadFile(note) }
                    )
                }
            }
        }

        // FAB
        Box(modifier = Modifier.fillMaxSize()) {
            FloatingActionButton(
                onClick = {
                    editingNote = null
                    showDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.tertiary,   // Cam nổi bật
                shape = RoundedCornerShape(20.dp),
                elevation = FloatingActionButtonDefaults.elevation(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm ghi chú", tint = Color.White)
            }
        }

        if (showDialog) {
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

    // ==================== NOTE CARD (thêm nút Tải về) ====================
    @Composable
    fun NoteCard(
        note: Note,
        onEdit: () -> Unit,
        onDelete: () -> Unit,
        onDownload: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F)),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFB3B3B3)
                )

                if (note.imageUrl.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AsyncImage(
                        model = note.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                if (note.fileUrl.isNotEmpty() && note.fileName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("📎 ${note.fileName}", color = MaterialTheme.colorScheme.secondary)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Sửa", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Xóa", fontWeight = FontWeight.SemiBold)
                    }

                    if (note.fileUrl.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onDownload,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Tải về")
                        }
                    }
                }
            }
        }
    }

    // ==================== NOTE DIALOG (hỗ trợ chọn File) ====================
    @Composable
    fun NoteDialog(
        note: Note? = null,
        onDismiss: () -> Unit,
        onSave: (String, String, Uri?, Uri?, String?) -> Unit
    ) {
        var title by remember { mutableStateOf(note?.title ?: "") }
        var description by remember { mutableStateOf(note?.description ?: "") }
        var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
        var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
        var selectedFileName by remember { mutableStateOf<String?>(null) }

        val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            selectedImageUri = uri
        }

        val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            selectedFileUri = uri
            selectedFileName = uri?.lastPathSegment?.substringAfterLast('/') ?: "file"
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (note == null) "Thêm ghi chú" else "Sửa ghi chú") },
            text = {
                Column {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Tiêu đề") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Nội dung") }, minLines = 3, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { imageLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                            Text("Chọn ảnh")
                        }
                        Button(onClick = { fileLauncher.launch("*/*") }, modifier = Modifier.weight(1f)) {
                            Text("Chọn file")
                        }
                    }

                    selectedImageUri?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    }

                    selectedFileUri?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("File đã chọn: ${selectedFileName ?: it.lastPathSegment}", color = Color(0xFF81C784))
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onSave(title, description, selectedImageUri, selectedFileUri, selectedFileName) }) {
                    Text(if (note == null) "Thêm" else "Lưu")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
        )
    }

    // ==================== DOWNLOAD FILE ====================
    private fun downloadFile(note: Note) {
        if (note.fileUrl.isEmpty()) return

        val context = this
        val request = DownloadManager.Request(Uri.parse(note.fileUrl))
            .setTitle(note.fileName)
            .setDescription("Đang tải ${note.fileName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, note.fileName)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        Toast.makeText(context, "Đang tải về thư mục Downloads...", Toast.LENGTH_SHORT).show()
    }

    // ==================== ADD / UPDATE NOTE (hỗ trợ file) ====================
    private fun addNoteWithFile(
        title: String, description: String,
        imageUri: Uri?, fileUri: Uri?, fileName: String?,
        onSuccess: () -> Unit
    ) {
        val noteData = hashMapOf<String, Any>(
            "title" to title,
            "description" to description,
            "timestamp" to FieldValue.serverTimestamp()
        )

        when {
            fileUri != null -> uploadToCloudinary(fileUri, true, fileName) { url ->
                noteData["fileUrl"] = url
                noteData["fileName"] = fileName ?: "file"
                noteRef.add(noteData).addOnSuccessListener { onSuccess() }
            }
            imageUri != null -> uploadToCloudinary(imageUri, false) { url ->
                noteData["imageUrl"] = url
                noteRef.add(noteData).addOnSuccessListener { onSuccess() }
            }
            else -> noteRef.add(noteData).addOnSuccessListener { onSuccess() }
        }
    }

    private fun updateNote(
        id: String, newTitle: String, newDesc: String,
        newImageUri: Uri?, newFileUri: Uri?, newFileName: String?,
        onSuccess: () -> Unit
    ) {
        val updates = hashMapOf<String, Any>(
            "title" to newTitle,
            "description" to newDesc
        )

        when {
            newFileUri != null -> uploadToCloudinary(newFileUri, true, newFileName) { url ->
                updates["fileUrl"] = url
                updates["fileName"] = newFileName ?: "file"
                noteRef.document(id).update(updates).addOnSuccessListener { onSuccess() }
            }
            newImageUri != null -> uploadToCloudinary(newImageUri, false) { url ->
                updates["imageUrl"] = url
                noteRef.document(id).update(updates).addOnSuccessListener { onSuccess() }
            }
            else -> noteRef.document(id).update(updates).addOnSuccessListener { onSuccess() }
        }
    }

    private fun uploadToCloudinary(uri: Uri, isFile: Boolean, fileName: String? = null, onComplete: (String) -> Unit) {
        val uploadRequest = MediaManager.get().upload(uri)

        uploadRequest.callback(object : UploadCallback {
            override fun onStart(requestId: String?) {}
            override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
            override fun onSuccess(requestId: String?, resultData: Map<*, *>) {
                val url = resultData["secure_url"].toString()
                onComplete(url)
            }
            override fun onError(requestId: String?, error: ErrorInfo?) {
                println("Cloudinary error: ${error?.description}")
            }
            override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
        }).dispatch()
    }

    // getNotes và deleteNote giữ nguyên, chỉ cần cập nhật mapping Note
    private fun getNotes(callback: (List<Note>) -> Unit) {
        noteRef.get().addOnSuccessListener { result ->
            val list = mutableListOf<Note>()
            for (doc in result) {
                val note = Note(
                    id = doc.id,
                    title = doc.getString("title") ?: "",
                    description = doc.getString("description") ?: "",
                    imageUrl = doc.getString("imageUrl") ?: "",
                    fileUrl = doc.getString("fileUrl") ?: "",
                    fileName = doc.getString("fileName") ?: ""
                )
                list.add(note)
            }
            callback(list)
        }.addOnFailureListener { it.printStackTrace() }
    }

    private fun deleteNote(id: String, onSuccess: () -> Unit) {
        noteRef.document(id).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { it.printStackTrace() }
    }
}