package com.example.noteapp

import android.net.Uri
import android.os.Bundle
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
import com.example.noteapp.ui.theme.NoteAppTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.UploadCallback
import com.cloudinary.android.callback.ErrorInfo
import kotlinx.coroutines.flow.distinctUntilChanged

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

    // ==================== THEO DÕI TRẠNG THÁI ĐĂNG NHẬP (SỬA ĐÚNG) ====================
    // ==================== THEO DÕI TRẠNG THÁI ĐĂNG NHẬP (SỬA ĐÚNG) ====================
    @Composable
    fun AuthStateScreen() {
        var user by remember { mutableStateOf(auth.currentUser) }

        // Sử dụng DisposableEffect để thêm và tự động remove listener
        DisposableEffect(auth) {
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                user = firebaseAuth.currentUser
            }
            auth.addAuthStateListener(listener)

            // Cleanup tự động khi Composable rời khỏi composition
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
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mật khẩu") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Vui lòng nhập đầy đủ", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isLoading = true

                    if (isLogin) {
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener {
                                isLoading = false
                                if (!it.isSuccessful) {
                                    Toast.makeText(context, "Đăng nhập thất bại", Toast.LENGTH_SHORT).show()
                                }
                            }
                    } else {
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener {
                                isLoading = false
                                if (it.isSuccessful) {
                                    Toast.makeText(context, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                                    isLogin = true
                                } else {
                                    Toast.makeText(context, "Đăng ký thất bại", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(if (isLogin) "Đăng nhập" else "Đăng ký")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { isLogin = !isLogin }) {
                Text(if (isLogin) "Chưa có tài khoản? Đăng ký" else "Đã có tài khoản? Đăng nhập")
            }
        }
    }

    // ==================== MÀN HÌNH CHÍNH ====================
    @Composable
    fun MainNoteScreen() {
        var notes by remember { mutableStateOf(listOf<Note>()) }
        var showDialog by remember { mutableStateOf(false) }
        var editingNote by remember { mutableStateOf<Note?>(null) }

        LaunchedEffect(Unit) {
            getNotes { notes = it }
        }

        Column(modifier = Modifier.fillMaxSize()) {

            // Top Bar với nút Đăng xuất
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E1E1E),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Notes",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    TextButton(onClick = { auth.signOut() }) {
                        Text("Đăng xuất", color = Color.White)
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
                        onDelete = { deleteNote(note.id) { getNotes { notes = it } } }
                    )
                }
            }
        }

        // Nút Add nổi
        Box(modifier = Modifier.fillMaxSize()) {
            FloatingActionButton(
                onClick = {
                    editingNote = null
                    showDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = Color(0xFFFF9800)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm", tint = Color.White)
            }
        }

        if (showDialog) {
            NoteDialog(
                note = editingNote,
                onDismiss = {
                    showDialog = false
                    editingNote = null
                },
                onSave = { title, desc, imageUri ->
                    if (editingNote == null) {
                        addNoteWithImage(title, desc, imageUri) { getNotes { notes = it } }
                    } else {
                        updateNote(editingNote!!.id, title, desc, imageUri) { getNotes { notes = it } }
                    }
                    showDialog = false
                    editingNote = null
                }
            )
        }
    }

    // ==================== NoteCard, NoteDialog, addNoteWithImage, updateNote, getNotes, deleteNote ====================
    @Composable
    fun NoteCard(note: Note, onEdit: () -> Unit, onDelete: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = note.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = note.description, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB0B0B0))

                if (note.imageUrl.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AsyncImage(
                        model = note.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Text("Sửa")
                    }
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Xóa")
                    }
                }
            }
        }
    }

    @Composable
    fun NoteDialog(
        note: Note? = null,
        onDismiss: () -> Unit,
        onSave: (String, String, Uri?) -> Unit
    ) {
        var title by remember { mutableStateOf(note?.title ?: "") }
        var description by remember { mutableStateOf(note?.description ?: "") }
        var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            selectedImageUri = uri
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

                    Button(onClick = { launcher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Chọn / Thay hình ảnh")
                    }

                    selectedImageUri?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onSave(title, description, selectedImageUri) }) {
                    Text(if (note == null) "Thêm" else "Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Hủy") }
            }
        )
    }

    // ==================== HÀM XỬ LÝ DỮ LIỆU (GIỮ NGUYÊN) ====================
    private fun addNoteWithImage(title: String, description: String, imageUri: Uri?, onSuccess: () -> Unit) {
        val noteData = hashMapOf<String, Any>(
            "title" to title,
            "description" to description,
            "timestamp" to FieldValue.serverTimestamp()
        )

        if (imageUri == null) {
            noteRef.add(noteData)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { it.printStackTrace() }
            return
        }

        MediaManager.get().upload(imageUri)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: Map<*, *>) {
                    val imageUrl = resultData["secure_url"].toString()
                    noteData["imageUrl"] = imageUrl
                    noteRef.add(noteData)
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { it.printStackTrace() }
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    println("Cloudinary upload lỗi: ${error?.description}")
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun updateNote(id: String, newTitle: String, newDesc: String, newImageUri: Uri?, onSuccess: () -> Unit) {
        val updates = hashMapOf<String, Any>(
            "title" to newTitle,
            "description" to newDesc
        )

        if (newImageUri == null) {
            noteRef.document(id).update(updates)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { it.printStackTrace() }
            return
        }

        MediaManager.get().upload(newImageUri)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: Map<*, *>) {
                    val newImageUrl = resultData["secure_url"].toString()
                    updates["imageUrl"] = newImageUrl
                    noteRef.document(id).update(updates)
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { it.printStackTrace() }
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    println("Upload lỗi: ${error?.description}")
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun getNotes(callback: (List<Note>) -> Unit) {
        noteRef.get().addOnSuccessListener { result ->
            val list = mutableListOf<Note>()
            for (doc in result) {
                val note = Note(
                    id = doc.id,
                    title = doc.getString("title") ?: "",
                    description = doc.getString("description") ?: "",
                    imageUrl = doc.getString("imageUrl") ?: ""
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