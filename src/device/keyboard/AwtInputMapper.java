package device.keyboard;

import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLScancode;

public final class AwtInputMapper {
	public static final int KEY_UNDEFINED = EmuKey.VK_UNDEFINED;
	public static final char CHAR_UNDEFINED = EmuKey.CHAR_UNDEFINED;

	private AwtInputMapper() {
	}

	public static int toAwtModifiersFromSdl(short sdlMods) {
		int mods = 0;
		if( (sdlMods&SDLKeycode.SDL_KMOD_SHIFT)!=0 )
			mods |= EmuKey.SHIFT_MASK;
		if( (sdlMods&SDLKeycode.SDL_KMOD_CTRL)!=0 )
			mods |= EmuKey.CTRL_MASK;
		if( (sdlMods&SDLKeycode.SDL_KMOD_ALT)!=0 )
			mods |= EmuKey.ALT_MASK;
		if( (sdlMods&SDLKeycode.SDL_KMOD_GUI)!=0 )
			mods |= EmuKey.META_MASK;
		return mods;
	}

	public static char mapSdlKeyChar(int sdlKeycode) {
		if( sdlKeycode >= 32 && sdlKeycode <= 126 ) {
			char out = (char) sdlKeycode;
			return Character.isLetter(out) ? Character.toLowerCase(out) : out;
		}
		return EmuKey.CHAR_UNDEFINED;
	}

	public static int toAwtKeyCodeFromSdlScancode(int scancode) {
		switch( scancode ) {
			case SDLScancode.SDL_SCANCODE_A: return EmuKey.VK_A;
			case SDLScancode.SDL_SCANCODE_B: return EmuKey.VK_B;
			case SDLScancode.SDL_SCANCODE_C: return EmuKey.VK_C;
			case SDLScancode.SDL_SCANCODE_D: return EmuKey.VK_D;
			case SDLScancode.SDL_SCANCODE_E: return EmuKey.VK_E;
			case SDLScancode.SDL_SCANCODE_F: return EmuKey.VK_F;
			case SDLScancode.SDL_SCANCODE_G: return EmuKey.VK_G;
			case SDLScancode.SDL_SCANCODE_H: return EmuKey.VK_H;
			case SDLScancode.SDL_SCANCODE_I: return EmuKey.VK_I;
			case SDLScancode.SDL_SCANCODE_J: return EmuKey.VK_J;
			case SDLScancode.SDL_SCANCODE_K: return EmuKey.VK_K;
			case SDLScancode.SDL_SCANCODE_L: return EmuKey.VK_L;
			case SDLScancode.SDL_SCANCODE_M: return EmuKey.VK_M;
			case SDLScancode.SDL_SCANCODE_N: return EmuKey.VK_N;
			case SDLScancode.SDL_SCANCODE_O: return EmuKey.VK_O;
			case SDLScancode.SDL_SCANCODE_P: return EmuKey.VK_P;
			case SDLScancode.SDL_SCANCODE_Q: return EmuKey.VK_Q;
			case SDLScancode.SDL_SCANCODE_R: return EmuKey.VK_R;
			case SDLScancode.SDL_SCANCODE_S: return EmuKey.VK_S;
			case SDLScancode.SDL_SCANCODE_T: return EmuKey.VK_T;
			case SDLScancode.SDL_SCANCODE_U: return EmuKey.VK_U;
			case SDLScancode.SDL_SCANCODE_V: return EmuKey.VK_V;
			case SDLScancode.SDL_SCANCODE_W: return EmuKey.VK_W;
			case SDLScancode.SDL_SCANCODE_X: return EmuKey.VK_X;
			case SDLScancode.SDL_SCANCODE_Y: return EmuKey.VK_Y;
			case SDLScancode.SDL_SCANCODE_Z: return EmuKey.VK_Z;
			case SDLScancode.SDL_SCANCODE_0: return EmuKey.VK_0;
			case SDLScancode.SDL_SCANCODE_1: return EmuKey.VK_1;
			case SDLScancode.SDL_SCANCODE_2: return EmuKey.VK_2;
			case SDLScancode.SDL_SCANCODE_3: return EmuKey.VK_3;
			case SDLScancode.SDL_SCANCODE_4: return EmuKey.VK_4;
			case SDLScancode.SDL_SCANCODE_5: return EmuKey.VK_5;
			case SDLScancode.SDL_SCANCODE_6: return EmuKey.VK_6;
			case SDLScancode.SDL_SCANCODE_7: return EmuKey.VK_7;
			case SDLScancode.SDL_SCANCODE_8: return EmuKey.VK_8;
			case SDLScancode.SDL_SCANCODE_9: return EmuKey.VK_9;
			case SDLScancode.SDL_SCANCODE_MINUS: return EmuKey.VK_MINUS;
			case SDLScancode.SDL_SCANCODE_EQUALS: return EmuKey.VK_EQUALS;
			case SDLScancode.SDL_SCANCODE_LEFTBRACKET: return EmuKey.VK_OPEN_BRACKET;
			case SDLScancode.SDL_SCANCODE_RIGHTBRACKET: return EmuKey.VK_CLOSE_BRACKET;
			case SDLScancode.SDL_SCANCODE_BACKSLASH: return EmuKey.VK_BACK_SLASH;
			case SDLScancode.SDL_SCANCODE_SEMICOLON: return EmuKey.VK_SEMICOLON;
			case SDLScancode.SDL_SCANCODE_APOSTROPHE: return EmuKey.VK_QUOTE;
			case SDLScancode.SDL_SCANCODE_COMMA: return EmuKey.VK_COMMA;
			case SDLScancode.SDL_SCANCODE_PERIOD: return EmuKey.VK_PERIOD;
			case SDLScancode.SDL_SCANCODE_SLASH: return EmuKey.VK_SLASH;
			case SDLScancode.SDL_SCANCODE_GRAVE: return EmuKey.VK_BACK_QUOTE;
			case SDLScancode.SDL_SCANCODE_RETURN: return EmuKey.VK_ENTER;
			case SDLScancode.SDL_SCANCODE_BACKSPACE: return EmuKey.VK_BACK_SPACE;
			case SDLScancode.SDL_SCANCODE_TAB: return EmuKey.VK_TAB;
			case SDLScancode.SDL_SCANCODE_ESCAPE: return EmuKey.VK_ESCAPE;
			case SDLScancode.SDL_SCANCODE_SPACE: return EmuKey.VK_SPACE;
			case SDLScancode.SDL_SCANCODE_LEFT: return EmuKey.VK_LEFT;
			case SDLScancode.SDL_SCANCODE_RIGHT: return EmuKey.VK_RIGHT;
			case SDLScancode.SDL_SCANCODE_UP: return EmuKey.VK_UP;
			case SDLScancode.SDL_SCANCODE_DOWN: return EmuKey.VK_DOWN;
			case SDLScancode.SDL_SCANCODE_LSHIFT:
			case SDLScancode.SDL_SCANCODE_RSHIFT: return EmuKey.VK_SHIFT;
			case SDLScancode.SDL_SCANCODE_LCTRL:
			case SDLScancode.SDL_SCANCODE_RCTRL: return EmuKey.VK_CONTROL;
			case SDLScancode.SDL_SCANCODE_CAPSLOCK: return EmuKey.VK_CAPS_LOCK;
			case SDLScancode.SDL_SCANCODE_LALT:
			case SDLScancode.SDL_SCANCODE_RALT: return EmuKey.VK_ALT;
			case SDLScancode.SDL_SCANCODE_LGUI:
			case SDLScancode.SDL_SCANCODE_RGUI: return EmuKey.VK_META;
			case SDLScancode.SDL_SCANCODE_INSERT: return EmuKey.VK_INSERT;
			case SDLScancode.SDL_SCANCODE_F1: return EmuKey.VK_F1;
			case SDLScancode.SDL_SCANCODE_F2: return EmuKey.VK_F2;
			case SDLScancode.SDL_SCANCODE_F3: return EmuKey.VK_F3;
			case SDLScancode.SDL_SCANCODE_F4: return EmuKey.VK_F4;
			case SDLScancode.SDL_SCANCODE_F5: return EmuKey.VK_F5;
			case SDLScancode.SDL_SCANCODE_F6: return EmuKey.VK_F6;
			case SDLScancode.SDL_SCANCODE_F7: return EmuKey.VK_F7;
			case SDLScancode.SDL_SCANCODE_F8: return EmuKey.VK_F8;
			case SDLScancode.SDL_SCANCODE_F9: return EmuKey.VK_F9;
			case SDLScancode.SDL_SCANCODE_F10: return EmuKey.VK_F10;
			case SDLScancode.SDL_SCANCODE_F11: return EmuKey.VK_F11;
			case SDLScancode.SDL_SCANCODE_F12: return EmuKey.VK_F12;
			case SDLScancode.SDL_SCANCODE_KP_0: return EmuKey.VK_NUMPAD0;
			case SDLScancode.SDL_SCANCODE_KP_1: return EmuKey.VK_NUMPAD1;
			case SDLScancode.SDL_SCANCODE_KP_2: return EmuKey.VK_NUMPAD2;
			case SDLScancode.SDL_SCANCODE_KP_3: return EmuKey.VK_NUMPAD3;
			case SDLScancode.SDL_SCANCODE_KP_4: return EmuKey.VK_NUMPAD4;
			case SDLScancode.SDL_SCANCODE_KP_5: return EmuKey.VK_NUMPAD5;
			case SDLScancode.SDL_SCANCODE_KP_6: return EmuKey.VK_NUMPAD6;
			case SDLScancode.SDL_SCANCODE_KP_7: return EmuKey.VK_NUMPAD7;
			case SDLScancode.SDL_SCANCODE_KP_8: return EmuKey.VK_NUMPAD8;
			case SDLScancode.SDL_SCANCODE_KP_9: return EmuKey.VK_NUMPAD9;
			case SDLScancode.SDL_SCANCODE_KP_PERIOD: return EmuKey.VK_DECIMAL;
			case SDLScancode.SDL_SCANCODE_KP_DIVIDE: return EmuKey.VK_DIVIDE;
			case SDLScancode.SDL_SCANCODE_KP_MULTIPLY: return EmuKey.VK_MULTIPLY;
			case SDLScancode.SDL_SCANCODE_KP_MINUS: return EmuKey.VK_SUBTRACT;
			case SDLScancode.SDL_SCANCODE_KP_PLUS: return EmuKey.VK_ADD;
			case SDLScancode.SDL_SCANCODE_KP_ENTER: return EmuKey.VK_ENTER;
			case SDLScancode.SDL_SCANCODE_KP_EQUALS: return EmuKey.VK_EQUALS;
			default: return EmuKey.VK_UNDEFINED;
		}
	}

	public static int toAwtKeyCodeFromSdlKeycode(int sdlKeycode) {
		char c = mapSdlKeyChar(sdlKeycode);
		if( c==CHAR_UNDEFINED )
			return KEY_UNDEFINED;
		if( Character.isLetter(c) )
			return EmuKey.VK_A + (Character.toUpperCase(c) - 'A');
		if( c>='0' && c<='9' )
			return EmuKey.VK_0 + (c - '0');
		switch( c ) {
			case ' ': return EmuKey.VK_SPACE;
			case ',': return EmuKey.VK_COMMA;
			case '.': return EmuKey.VK_PERIOD;
			case '/': return EmuKey.VK_SLASH;
			case ';': return EmuKey.VK_SEMICOLON;
			case '=': return EmuKey.VK_EQUALS;
			case '-': return EmuKey.VK_MINUS;
			case '[': return EmuKey.VK_OPEN_BRACKET;
			case ']': return EmuKey.VK_CLOSE_BRACKET;
			case '\\': return EmuKey.VK_BACK_SLASH;
			case '\'': return EmuKey.VK_QUOTE;
			case '`': return EmuKey.VK_BACK_QUOTE;
			default: return KEY_UNDEFINED;
		}
	}
}
