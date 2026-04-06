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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

class MainActivity : ComponentActivity() {

    private val auth: FirebaseAuth = Firebase.auth
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
            NoteAppTheme {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    AuthScreen()
                } else {
                    NoteMainScreen(currentUserEmail = currentUser.email ?: "User")
                }
            }
        }
    }

    // ==================== AUTH SCREEN ====================
    @Composable
    fun AuthScreen() {
        var isLogin by remember { mutableStateOf(true) }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(if (isLogin) "Đăng nhập" else "Đăng ký", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Mật khẩu") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = {
                isLoading = true
                if (isLogin) {
                    auth.signInWithEmailAndPassword(email, password).addOnCompleteListener {
                        isLoading = false
                        if (!it.isSuccessful) Toast.makeText(context, "Đăng nhập thất bại", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener {
                        isLoading = false
                        if (it.isSuccessful) {
                            Toast.makeText(context, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                            isLogin = true
                        } else Toast.makeText(context, "Đăng ký thất bại", Toast.LENGTH_SHORT).show()
                    }
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
                Text(if (isLogin) "Đăng nhập" else "Đăng ký")
            }

            TextButton(onClick = { isLogin = !isLogin }) {
                Text(if (isLogin) "Chưa có tài khoản? Đăng ký" else "Đã có tài khoản? Đăng nhập")
            }
        }
    }

    // ==================== MAIN SCREEN (Có nút Sửa) ====================
    @Composable
    fun NoteMainScreen(currentUserEmail: String) {
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
        var notes by remember { mutableStateOf(listOf<Note>()) }

        var showEditDialog by remember { mutableStateOf(false) }
        var editingNote by remember { mutableStateOf<Note?>(null) }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            selectedImageUri = uri
        }

        LaunchedEffect(Unit) {
            getNotes { notes = it }
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("📝 My Notes", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = { auth.signOut() }) { Text("Đăng xuất") }
            }

            Text("Xin chào: $currentUserEmail", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(16.dp))

            // Form thêm note
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Tiêu đề") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Nội dung") }, minLines = 2, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(onClick = { launcher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text("📷 Chọn hình ảnh")
                    }

                    selectedImageUri?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = {
                        if (title.isNotBlank()) {
                            addNoteWithImage(title, description, selectedImageUri) {
                                getNotes { notes = it }
                                title = ""
                                description = ""
                                selectedImageUri = null
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Thêm ghi chú")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Danh sách ghi chú (${notes.size})", style = MaterialTheme.typography.titleMedium)

            LazyColumn {
                items(notes) { note ->
                    NoteItem(
                        note = note,
                        onEdit = {
                            editingNote = note
                            showEditDialog = true
                        },
                        onDelete = {
                            deleteNote(note.id) { getNotes { notes = it } }
                        }
                    )
                }
            }
        }

        // Dialog sửa note
        if (showEditDialog && editingNote != null) {
            EditNoteDialog(
                note = editingNote!!,
                onDismiss = { showEditDialog = false; editingNote = null },
                onSave = { newTitle, newDesc, newImageUri ->
                    updateNote(editingNote!!.id, newTitle, newDesc, newImageUri) {
                        getNotes { notes = it }
                        showEditDialog = false
                        editingNote = null
                    }
                }
            )
        }
    }

    // ====================== DIALOG SỬA NOTE ======================
    @Composable
    fun EditNoteDialog(
        note: Note,
        onDismiss: () -> Unit,
        onSave: (String, String, Uri?) -> Unit
    ) {
        var newTitle by remember { mutableStateOf(note.title) }
        var newDescription by remember { mutableStateOf(note.description) }
        var newImageUri by remember { mutableStateOf<Uri?>(null) }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            newImageUri = uri
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Sửa ghi chú") },
            text = {
                Column {
                    OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("Tiêu đề") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newDescription, onValueChange = { newDescription = it }, label = { Text("Nội dung") }, minLines = 2, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(onClick = { launcher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Thay đổi hình ảnh")
                    }

                    newImageUri?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onSave(newTitle, newDescription, newImageUri) }) {
                    Text("Lưu thay đổi")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Hủy") }
            }
        )
    }

    // ====================== CẬP NHẬT NOTE (UPDATE) ======================
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

        // Nếu có ảnh mới → upload Cloudinary trước rồi cập nhật
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

    private fun addNoteWithImage(
        title: String,
        description: String,
        imageUri: Uri?,
        onSuccess: () -> Unit
    ) {
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
            })
            .dispatch()
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

// NoteItem có thêm nút Sửa
@Composable
fun NoteItem(note: Note, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = note.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = note.description, style = MaterialTheme.typography.bodyMedium)

            if (note.imageUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = note.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
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