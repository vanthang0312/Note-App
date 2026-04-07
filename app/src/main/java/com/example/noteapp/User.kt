package com.example.noteapp

data class User(
    val uid: String = "",
    val email: String = "",
    val role: String = "user" // Mặc định là user
)
