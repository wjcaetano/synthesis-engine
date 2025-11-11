package com.capco.brsp.synthesisengine.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class UtilsTest {
    @Test
    void testDetectLanguage() {
        String english = "Hello world! How are you?";
        String portuguese = "Olá, Mundo! Como vai?";
        String spanish = "¡Hola mundo! ¿Cómo estás?";
        String french = "Bonjour, le monde!";
        String german = "Hallo, Welt!";
        String italian = "Ciao a tutti! Come state?";
        String chinese = "你好，世界！你好吗？";
        String japanese = "こんにちは世界！お元気ですか？";
        String russian = "Привет, мир! Как дела? Всё в порядке?";
        String arabic = "مرحبا بالعالم!";

//        assertEquals("en", Utils.detectLanguage(english));
//        assertEquals("pt", Utils.detectLanguage(portuguese));
//        assertEquals("es", Utils.detectLanguage(spanish));
//        assertEquals("fr", Utils.detectLanguage(french));
//        assertEquals("de", Utils.detectLanguage(german));
//        assertEquals("it", Utils.detectLanguage(italian));
//        assertEquals("zh-CN", Utils.detectLanguage(chinese));
//        assertEquals("ja", Utils.detectLanguage(japanese));
//        assertEquals("ru", Utils.detectLanguage(russian));
//        assertEquals("ar", Utils.detectLanguage(arabic));
    }
}
