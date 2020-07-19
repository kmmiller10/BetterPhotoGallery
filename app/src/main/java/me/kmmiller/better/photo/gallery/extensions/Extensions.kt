package me.kmmiller.better.photo.gallery.extensions

fun <T, R> withNullable(receiver: T?, block: T.() -> R): R? = receiver?.block()