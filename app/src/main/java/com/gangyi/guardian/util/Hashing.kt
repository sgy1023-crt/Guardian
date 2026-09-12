package com.gangyi.guardian.util

import java.security.MessageDigest

/** 关键词密码只存 SHA-256 hex。这是"防自己"的门槛，不是对抗攻击者的加密。 */
fun sha256(s: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
