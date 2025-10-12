package com.rybki.spring_boot.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Base64Util {

    // Кодирование массива байт в Base64
    public static String encode(byte[] bytes) {
        if (bytes == null) return null;
        return Base64.getEncoder().encodeToString(bytes);
    }

    // Декодирование строки Base64 в массив байт
    public static byte[] decode(String base64) {
        if (base64 == null) return null;
        return Base64.getDecoder().decode(base64);
    }

    // Кодирование строки в Base64 (UTF-8)
    //public static String encode(String text) {
    //    if (text == null) return null;
    //    return encode(text.getBytes(StandardCharsets.UTF_8));
    //}

    // Декодирование строки Base64 обратно в текст (UTF-8)
    //public static String decodeToString(String base64) {
    //    if (base64 == null) return null;
    //    return new String(decode(base64), StandardCharsets.UTF_8);
    //}
}
