package com.example.noteapp

data class Note(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var imageUrl: String = "",      // vẫn giữ để hiển thị ảnh (nếu có)
    var fileUrl: String = "",       // URL của file trên Cloudinary (nếu có)
    var fileName: String = ""       // tên file gốc để hiển thị và tải về
)