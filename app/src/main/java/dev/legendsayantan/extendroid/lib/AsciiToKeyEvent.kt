package dev.legendsayantan.extendroid.lib

/**
 * @author legendsayantan
 */
import android.view.KeyEvent

/**
 * A utility object to map ASCII characters to Android KeyEvent keycodes.
 *
 * This object provides a comprehensive map and a convenience function to convert
 * a given character into its corresponding keycode.
 */
object AsciiToKeyEvent {
    fun buildKeyEvent(downTime:Long,eventTime:Long,action:Int,ascii:Int, metaState:Int): KeyEvent? {
        // In Kotlin, `Char.code` gives the integer (ASCII/Unicode) value.
        val requireMeta : (Int,Int) -> KeyEvent? = { meta,code ->
            if(KeyEvent.metaStateHasModifiers(metaState,meta))
                KeyEvent(downTime, eventTime, action, code, 0, metaState)
            else KeyEvent(downTime, eventTime, action, code, 0, metaState + meta)
        }
        val build : (Int) -> KeyEvent = { code ->
            KeyEvent(downTime, eventTime, action, code, 0, metaState)
        }

        when(ascii){
            8-> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_DEL)
            9-> return build(KeyEvent.KEYCODE_TAB)
            10, 13 -> return build(KeyEvent.KEYCODE_ENTER)
            27 -> return build(KeyEvent.KEYCODE_ESCAPE)
            32 -> return build(KeyEvent.KEYCODE_SPACE)
            33 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_1) // '!'
            34 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_APOSTROPHE) // '"'
            35 -> return build(KeyEvent.KEYCODE_POUND)
            36 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_4) // '$'
            37 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_5) // '%'
            38 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_7) // '&'
            39 -> return build(KeyEvent.KEYCODE_APOSTROPHE) // '\''
            40 -> return build(KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN) // '('
            41 -> return build(KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN) // ')'
            42 -> return build(KeyEvent.KEYCODE_STAR)
            43 -> return build(KeyEvent.KEYCODE_PLUS)
            44 -> return build(KeyEvent.KEYCODE_COMMA)
            45 -> return build(KeyEvent.KEYCODE_MINUS)
            46 -> return build(KeyEvent.KEYCODE_PERIOD)
            47 -> return build(KeyEvent.KEYCODE_SLASH)
            48 -> return build(KeyEvent.KEYCODE_0)
            49 -> return build(KeyEvent.KEYCODE_1)
            50 -> return build(KeyEvent.KEYCODE_2)
            51 -> return build(KeyEvent.KEYCODE_3)
            52 -> return build(KeyEvent.KEYCODE_4)
            53 -> return build(KeyEvent.KEYCODE_5)
            54 -> return build(KeyEvent.KEYCODE_6)
            55 -> return build(KeyEvent.KEYCODE_7)
            56 -> return build(KeyEvent.KEYCODE_8)
            57 -> return build(KeyEvent.KEYCODE_9)
            58 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_SEMICOLON) // ':'
            59 -> return build(KeyEvent.KEYCODE_SEMICOLON) // ';'
            60 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_COMMA) // '<'
            61 -> return build(KeyEvent.KEYCODE_EQUALS) // '='
            62 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_PERIOD) // '>'
            63 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_SLASH) // '?'
            64 -> return build(KeyEvent.KEYCODE_AT) // '@'
            65 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_A)
            66 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_B)
            67 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_C)
            68 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_D)
            69 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_E)
            70 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_F)
            71 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_G)
            72 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_H)
            73 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_I)
            74 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_J)
            75 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_K)
            76 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_L)
            77 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_M)
            78 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_N)
            79 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_O)
            80 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_P)
            81 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_Q)
            82 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_R)
            83 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_S)
            84 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_T)
            85 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_U)
            86 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_V)
            87 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_W)
            88 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_X)
            89 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_Y)
            90 -> return requireMeta(KeyEvent.META_SHIFT_ON,KeyEvent.KEYCODE_Z)
            91 -> return build(KeyEvent.KEYCODE_LEFT_BRACKET) // '['
            92 -> return build(KeyEvent.KEYCODE_BACKSLASH) // '\'
            93 -> return build(KeyEvent.KEYCODE_RIGHT_BRACKET) // ']'
            94 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_6) // '^'
            95 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_MINUS) // '_'
            96 -> return build(KeyEvent.KEYCODE_GRAVE) // '`'
            97 -> return build(KeyEvent.KEYCODE_A)
            98 -> return build(KeyEvent.KEYCODE_B)
            99 -> return build(KeyEvent.KEYCODE_C)
            100 -> return build(KeyEvent.KEYCODE_D)
            101 -> return build(KeyEvent.KEYCODE_E)
            102 -> return build(KeyEvent.KEYCODE_F)
            103 -> return build(KeyEvent.KEYCODE_G)
            104 -> return build(KeyEvent.KEYCODE_H)
            105 -> return build(KeyEvent.KEYCODE_I)
            106 -> return build(KeyEvent.KEYCODE_J)
            107 -> return build(KeyEvent.KEYCODE_K)
            108 -> return build(KeyEvent.KEYCODE_L)
            109 -> return build(KeyEvent.KEYCODE_M)
            110 -> return build(KeyEvent.KEYCODE_N)
            111 -> return build(KeyEvent.KEYCODE_O)
            112 -> return build(KeyEvent.KEYCODE_P)
            113 -> return build(KeyEvent.KEYCODE_Q)
            114 -> return build(KeyEvent.KEYCODE_R)
            115 -> return build(KeyEvent.KEYCODE_S)
            116 -> return build(KeyEvent.KEYCODE_T)
            117 -> return build(KeyEvent.KEYCODE_U)
            118 -> return build(KeyEvent.KEYCODE_V)
            119 -> return build(KeyEvent.KEYCODE_W)
            120 -> return build(KeyEvent.KEYCODE_X)
            121 -> return build(KeyEvent.KEYCODE_Y)
            122 -> return build(KeyEvent.KEYCODE_Z)
            123 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_LEFT_BRACKET) // '{'
            124 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_BACKSLASH) // '|'
            125 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_RIGHT_BRACKET) // '}'
            126 -> return requireMeta(KeyEvent.META_SHIFT_ON, KeyEvent.KEYCODE_GRAVE) // '~'
            127 -> return build(KeyEvent.KEYCODE_FORWARD_DEL) // Delete

            else -> return null // No mapping exists for this ASCII value
        }
    }
}