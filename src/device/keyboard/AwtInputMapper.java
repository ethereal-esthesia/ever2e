package device.keyboard;

import java.awt.Event;
import java.awt.event.KeyEvent;

import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLScancode;

public final class AwtInputMapper {
	public static final int KEY_UNDEFINED = KeyEvent.VK_UNDEFINED;
	public static final char CHAR_UNDEFINED = KeyEvent.CHAR_UNDEFINED;

	private AwtInputMapper() {
	}

	public static int toAwtModifiersFromSdl(short sdlMods) {
		int mods = 0;
		if( (sdlMods&SDLKeycode.SDL_KMOD_SHIFT)!=0 )
			mods |= Event.SHIFT_MASK;
		if( (sdlMods&SDLKeycode.SDL_KMOD_CTRL)!=0 )
			mods |= Event.CTRL_MASK;
		if( (sdlMods&SDLKeycode.SDL_KMOD_ALT)!=0 )
			mods |= Event.ALT_MASK;
		if( (sdlMods&SDLKeycode.SDL_KMOD_GUI)!=0 )
			mods |= Event.META_MASK;
		if( (sdlMods&SDLKeycode.SDL_KMOD_CAPS)!=0 )
			mods |= Event.CAPS_LOCK;
		return mods;
	}

	public static char mapSdlKeyChar(int sdlKeycode) {
		if( sdlKeycode >= 32 && sdlKeycode <= 126 ) {
			char out = (char) sdlKeycode;
			return Character.isLetter(out) ? Character.toLowerCase(out) : out;
		}
		return KeyEvent.CHAR_UNDEFINED;
	}

	public static int toAwtKeyCodeFromSdlScancode(int scancode) {
		switch( scancode ) {
			case SDLScancode.SDL_SCANCODE_A: return KeyEvent.VK_A;
			case SDLScancode.SDL_SCANCODE_B: return KeyEvent.VK_B;
			case SDLScancode.SDL_SCANCODE_C: return KeyEvent.VK_C;
			case SDLScancode.SDL_SCANCODE_D: return KeyEvent.VK_D;
			case SDLScancode.SDL_SCANCODE_E: return KeyEvent.VK_E;
			case SDLScancode.SDL_SCANCODE_F: return KeyEvent.VK_F;
			case SDLScancode.SDL_SCANCODE_G: return KeyEvent.VK_G;
			case SDLScancode.SDL_SCANCODE_H: return KeyEvent.VK_H;
			case SDLScancode.SDL_SCANCODE_I: return KeyEvent.VK_I;
			case SDLScancode.SDL_SCANCODE_J: return KeyEvent.VK_J;
			case SDLScancode.SDL_SCANCODE_K: return KeyEvent.VK_K;
			case SDLScancode.SDL_SCANCODE_L: return KeyEvent.VK_L;
			case SDLScancode.SDL_SCANCODE_M: return KeyEvent.VK_M;
			case SDLScancode.SDL_SCANCODE_N: return KeyEvent.VK_N;
			case SDLScancode.SDL_SCANCODE_O: return KeyEvent.VK_O;
			case SDLScancode.SDL_SCANCODE_P: return KeyEvent.VK_P;
			case SDLScancode.SDL_SCANCODE_Q: return KeyEvent.VK_Q;
			case SDLScancode.SDL_SCANCODE_R: return KeyEvent.VK_R;
			case SDLScancode.SDL_SCANCODE_S: return KeyEvent.VK_S;
			case SDLScancode.SDL_SCANCODE_T: return KeyEvent.VK_T;
			case SDLScancode.SDL_SCANCODE_U: return KeyEvent.VK_U;
			case SDLScancode.SDL_SCANCODE_V: return KeyEvent.VK_V;
			case SDLScancode.SDL_SCANCODE_W: return KeyEvent.VK_W;
			case SDLScancode.SDL_SCANCODE_X: return KeyEvent.VK_X;
			case SDLScancode.SDL_SCANCODE_Y: return KeyEvent.VK_Y;
			case SDLScancode.SDL_SCANCODE_Z: return KeyEvent.VK_Z;
			case SDLScancode.SDL_SCANCODE_0: return KeyEvent.VK_0;
			case SDLScancode.SDL_SCANCODE_1: return KeyEvent.VK_1;
			case SDLScancode.SDL_SCANCODE_2: return KeyEvent.VK_2;
			case SDLScancode.SDL_SCANCODE_3: return KeyEvent.VK_3;
			case SDLScancode.SDL_SCANCODE_4: return KeyEvent.VK_4;
			case SDLScancode.SDL_SCANCODE_5: return KeyEvent.VK_5;
			case SDLScancode.SDL_SCANCODE_6: return KeyEvent.VK_6;
			case SDLScancode.SDL_SCANCODE_7: return KeyEvent.VK_7;
			case SDLScancode.SDL_SCANCODE_8: return KeyEvent.VK_8;
			case SDLScancode.SDL_SCANCODE_9: return KeyEvent.VK_9;
			case SDLScancode.SDL_SCANCODE_MINUS: return KeyEvent.VK_MINUS;
			case SDLScancode.SDL_SCANCODE_EQUALS: return KeyEvent.VK_EQUALS;
			case SDLScancode.SDL_SCANCODE_LEFTBRACKET: return KeyEvent.VK_OPEN_BRACKET;
			case SDLScancode.SDL_SCANCODE_RIGHTBRACKET: return KeyEvent.VK_CLOSE_BRACKET;
			case SDLScancode.SDL_SCANCODE_BACKSLASH: return KeyEvent.VK_BACK_SLASH;
			case SDLScancode.SDL_SCANCODE_SEMICOLON: return KeyEvent.VK_SEMICOLON;
			case SDLScancode.SDL_SCANCODE_APOSTROPHE: return KeyEvent.VK_QUOTE;
			case SDLScancode.SDL_SCANCODE_COMMA: return KeyEvent.VK_COMMA;
			case SDLScancode.SDL_SCANCODE_PERIOD: return KeyEvent.VK_PERIOD;
			case SDLScancode.SDL_SCANCODE_SLASH: return KeyEvent.VK_SLASH;
			case SDLScancode.SDL_SCANCODE_GRAVE: return KeyEvent.VK_BACK_QUOTE;
			case SDLScancode.SDL_SCANCODE_RETURN: return KeyEvent.VK_ENTER;
			case SDLScancode.SDL_SCANCODE_BACKSPACE: return KeyEvent.VK_BACK_SPACE;
			case SDLScancode.SDL_SCANCODE_TAB: return KeyEvent.VK_TAB;
			case SDLScancode.SDL_SCANCODE_ESCAPE: return KeyEvent.VK_ESCAPE;
			case SDLScancode.SDL_SCANCODE_SPACE: return KeyEvent.VK_SPACE;
			case SDLScancode.SDL_SCANCODE_LEFT: return KeyEvent.VK_LEFT;
			case SDLScancode.SDL_SCANCODE_RIGHT: return KeyEvent.VK_RIGHT;
			case SDLScancode.SDL_SCANCODE_UP: return KeyEvent.VK_UP;
			case SDLScancode.SDL_SCANCODE_DOWN: return KeyEvent.VK_DOWN;
			case SDLScancode.SDL_SCANCODE_LSHIFT:
			case SDLScancode.SDL_SCANCODE_RSHIFT: return KeyEvent.VK_SHIFT;
			case SDLScancode.SDL_SCANCODE_LCTRL:
			case SDLScancode.SDL_SCANCODE_RCTRL: return KeyEvent.VK_CONTROL;
			case SDLScancode.SDL_SCANCODE_CAPSLOCK: return KeyEvent.VK_CAPS_LOCK;
			case SDLScancode.SDL_SCANCODE_LALT:
			case SDLScancode.SDL_SCANCODE_RALT: return KeyEvent.VK_ALT;
			case SDLScancode.SDL_SCANCODE_LGUI:
			case SDLScancode.SDL_SCANCODE_RGUI: return KeyEvent.VK_META;
			case SDLScancode.SDL_SCANCODE_INSERT: return KeyEvent.VK_INSERT;
			case SDLScancode.SDL_SCANCODE_F1: return KeyEvent.VK_F1;
			case SDLScancode.SDL_SCANCODE_F2: return KeyEvent.VK_F2;
			case SDLScancode.SDL_SCANCODE_F3: return KeyEvent.VK_F3;
			case SDLScancode.SDL_SCANCODE_F4: return KeyEvent.VK_F4;
			case SDLScancode.SDL_SCANCODE_F5: return KeyEvent.VK_F5;
			case SDLScancode.SDL_SCANCODE_F6: return KeyEvent.VK_F6;
			case SDLScancode.SDL_SCANCODE_F7: return KeyEvent.VK_F7;
			case SDLScancode.SDL_SCANCODE_F8: return KeyEvent.VK_F8;
			case SDLScancode.SDL_SCANCODE_F9: return KeyEvent.VK_F9;
			case SDLScancode.SDL_SCANCODE_F10: return KeyEvent.VK_F10;
			case SDLScancode.SDL_SCANCODE_F11: return KeyEvent.VK_F11;
			case SDLScancode.SDL_SCANCODE_F12: return KeyEvent.VK_F12;
			case SDLScancode.SDL_SCANCODE_KP_0: return KeyEvent.VK_NUMPAD0;
			case SDLScancode.SDL_SCANCODE_KP_1: return KeyEvent.VK_NUMPAD1;
			case SDLScancode.SDL_SCANCODE_KP_2: return KeyEvent.VK_NUMPAD2;
			case SDLScancode.SDL_SCANCODE_KP_3: return KeyEvent.VK_NUMPAD3;
			case SDLScancode.SDL_SCANCODE_KP_4: return KeyEvent.VK_NUMPAD4;
			case SDLScancode.SDL_SCANCODE_KP_5: return KeyEvent.VK_NUMPAD5;
			case SDLScancode.SDL_SCANCODE_KP_6: return KeyEvent.VK_NUMPAD6;
			case SDLScancode.SDL_SCANCODE_KP_7: return KeyEvent.VK_NUMPAD7;
			case SDLScancode.SDL_SCANCODE_KP_8: return KeyEvent.VK_NUMPAD8;
			case SDLScancode.SDL_SCANCODE_KP_9: return KeyEvent.VK_NUMPAD9;
			case SDLScancode.SDL_SCANCODE_KP_PERIOD: return KeyEvent.VK_DECIMAL;
			case SDLScancode.SDL_SCANCODE_KP_DIVIDE: return KeyEvent.VK_DIVIDE;
			case SDLScancode.SDL_SCANCODE_KP_MULTIPLY: return KeyEvent.VK_MULTIPLY;
			case SDLScancode.SDL_SCANCODE_KP_MINUS: return KeyEvent.VK_SUBTRACT;
			case SDLScancode.SDL_SCANCODE_KP_PLUS: return KeyEvent.VK_ADD;
			case SDLScancode.SDL_SCANCODE_KP_ENTER: return KeyEvent.VK_ENTER;
			case SDLScancode.SDL_SCANCODE_KP_EQUALS: return KeyEvent.VK_EQUALS;
			default: return KeyEvent.VK_UNDEFINED;
		}
	}
}
