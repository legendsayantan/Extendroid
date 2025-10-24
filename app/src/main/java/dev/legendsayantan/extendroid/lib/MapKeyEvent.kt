package dev.legendsayantan.extendroid.lib

/**
 * @author legendsayantan
 */
import android.os.Build
import android.view.KeyEvent

/**
 * A utility object to map ASCII characters to Android KeyEvent keycodes.
 *
 * This object provides a comprehensive map and a convenience function to convert
 * a given character into its corresponding keycode.
 */
object MapKeyEvent {
    fun requireMeta (meta : Int,originalMeta : Int) : Int{
        return if(KeyEvent.metaStateHasModifiers(originalMeta,meta)) originalMeta
        else meta + originalMeta
    }
    fun buildKeyEvent(downTime:Long, eventTime:Long, action:Int, webKeyCode:Int, metaState:Int): KeyEvent? {
        return when(webKeyCode){
            8 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_DEL,0, metaState)
            9 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_TAB,0, metaState)
            10, 13 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_ENTER,0, metaState)
            16 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_SHIFT_LEFT,0, if(action== KeyEvent.ACTION_DOWN) requireMeta(KeyEvent.META_SHIFT_ON, metaState) else metaState)
            17 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_CTRL_LEFT,0, if(action== KeyEvent.ACTION_DOWN) requireMeta(KeyEvent.META_CTRL_ON, metaState) else metaState)
            18 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_ALT_LEFT,0, if(action== KeyEvent.ACTION_DOWN) requireMeta(KeyEvent.META_ALT_ON, metaState) else metaState)
            20 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_CAPS_LOCK,0, metaState)
            27 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_ESCAPE,0, metaState)
            32 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_SPACE,0, metaState)
            33 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_PAGE_UP,0, metaState)
            34 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_PAGE_DOWN,0, metaState)
            35 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_MOVE_END,0, metaState)
            36 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_MOVE_HOME,0, metaState)
            37 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_DPAD_LEFT,0, metaState)
            38 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_DPAD_UP,0, metaState)
            39 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_DPAD_RIGHT,0, metaState)
            40 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_DPAD_DOWN,0, metaState)
            41 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN,0, metaState)
            42 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_STAR,0, metaState)
            43 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_PLUS,0, metaState)
            44 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) { KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_SCREENSHOT,0, metaState) }else null
            45 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_MINUS,0, metaState)
            46 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_FORWARD_DEL,0, metaState)
            47 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_SLASH,0, metaState)
            48 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_0,0, metaState)
            49 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_1,0, metaState)
            50 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_2,0, metaState)
            51 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_3,0, metaState)
            52 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_4,0, metaState)
            53 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_5,0, metaState)
            54 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_6,0, metaState)
            55 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_7,0, metaState)
            56 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_8,0, metaState)
            57 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_9,0, metaState)

            65 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_A,0, metaState)
            66 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_B,0, metaState)
            67 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_C,0, metaState)
            68 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_D,0, metaState)
            69 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_E,0, metaState)
            70 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_F,0, metaState)
            71 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_G,0, metaState)
            72 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_H,0, metaState)
            73 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_I,0, metaState)
            74 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_J,0, metaState)
            75 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_K,0, metaState)
            76 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_L,0, metaState)
            77 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_M,0, metaState)
            78 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_N,0, metaState)
            79 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_O,0, metaState)
            80 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_P,0, metaState)
            81 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_Q,0, metaState)
            82 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_R,0, metaState)
            83 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_S,0, metaState)
            84 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_T,0, metaState)
            85 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_U,0, metaState)
            86 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_V,0, metaState)
            87 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_W,0, metaState)
            88 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_X,0, metaState)
            89 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_Y,0, metaState)
            90 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_Z,0, metaState)
            91 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_META_LEFT,0, if(action== KeyEvent.ACTION_DOWN) requireMeta(KeyEvent.META_META_ON, metaState) else metaState)

            96 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_0,0, metaState)
            97 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_1,0, metaState)
            98 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_2,0, metaState)
            99 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_3,0, metaState)
            100 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_4,0, metaState)
            101 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_5,0, metaState)
            102 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_6,0, metaState)
            103 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_7,0, metaState)
            104 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_8,0, metaState)
            105 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_9,0, metaState)
            106 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_MULTIPLY,0, metaState)
            107 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_ADD,0, metaState)

            109 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_SUBTRACT,0, metaState)
            110 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_DOT,0, metaState)
            111 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUMPAD_DIVIDE,0, metaState)
            112 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_F1,0, metaState)
            113 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_F2,0, metaState)
            114 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_F3,0, metaState)
            115 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_F4,0, metaState)
            116 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_F5,0, metaState)
            117 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_F6,0, metaState)
            118 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_F7,0, metaState)
            119 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_F8,0, metaState)
            120 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_F9,0, metaState)
            121 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_F10,0, metaState)
            122 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_F11,0, metaState)
            123 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_F12,0, metaState)
            144 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_NUM_LOCK,0, metaState)

            186 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_SEMICOLON,0, metaState)
            187 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_EQUALS,0, metaState)
            188 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_COMMA,0, metaState)
            189 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_MINUS,0, metaState)
            190 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_PERIOD,0, metaState)
            191 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_SLASH,0, metaState)
            192 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_GRAVE,0, metaState)
            219 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_LEFT_BRACKET,0, metaState)
            220 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_BACKSLASH,0, metaState)
            221 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_RIGHT_BRACKET,0, metaState)
            222 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_APOSTROPHE,0, metaState)

            //action keys
            -1 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_BACK,0, metaState)
            -2 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_FORWARD,0, metaState)
            -3 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_CUT,0, metaState)
            -4 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_COPY,0, metaState)
            -5 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_PASTE,0, metaState)
            -6 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_POWER,0, metaState)
            -7 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_VOLUME_DOWN,0, metaState)
            -8 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_VOLUME_UP,0, metaState)
            -9 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_VOLUME_MUTE,0, metaState)
            -10 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_MENU,0, metaState)
            -11 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_MEDIA_PREVIOUS,0, metaState)
            -12 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,0, metaState)
            -13 -> KeyEvent(downTime, eventTime, action,KeyEvent.KEYCODE_MEDIA_NEXT,0, metaState)

            else -> null // No mapping exists for this ASCII value
        }
    }
}