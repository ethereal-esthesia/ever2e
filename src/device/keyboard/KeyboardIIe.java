package device.keyboard;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.lwjgl.sdl.SDLClipboard;

import core.cpu.cpu8.Cpu65c02;
import core.exception.HardwareException;

public class KeyboardIIe extends Keyboard {
	private static volatile boolean keyLoggingEnabled;

	private Cpu65c02 cpu;

	private ConcurrentLinkedQueue<Integer> keyEventQueue = new ConcurrentLinkedQueue<>();
	private Queue<Byte> keyQueue = new LinkedList<>();
	private Queue<Boolean> pasteMarkerQueue = new LinkedList<>();

	private int modifierSet;
	private int functionKeySet;
	private boolean capsLockState;
	private boolean optionKey;
	private boolean appleKey;
	private byte keyCode;
	private int keyCount;
	private byte modIndex;
	private boolean isHalted;
	private int cycleCount;
	private int delayCount;
	private long queuedKeyCount;
	private long consumedQueuedKeyCount;
	private int pendingPastedKeys;
	private boolean pasteInputSuppressed;
	
	private static final int PRE_REPEAT_FLOP_COUNT = 11;
	private static final int FLOP_DELAY_CYCLES = 4;
	
	private static final int KEY_MASK_CAPS = 0x00000001;
	private static final int KEY_MASK_SHIFT = 0x00000002;
	private static final int KEY_MASK_CTRL = 0x00000004;

	private static final int KEY_MASK_F1 = 0x00000100;
	private static final int KEY_MASK_F2 = 0x00000200;
	private static final int KEY_MASK_F3 = 0x00000400;
	private static final int KEY_MASK_F4 = 0x00000800;
	private static final int KEY_MASK_F5 = 0x00001000;
	private static final int KEY_MASK_F6 = 0x00002000;
	private static final int KEY_MASK_F7 = 0x00004000;
	private static final int KEY_MASK_F8 = 0x00008000;
	private static final int KEY_MASK_F9 = 0x00010000;
	private static final int KEY_MASK_F10 = 0x00020000;
	private static final int KEY_MASK_F12 = 0x00080000;
	private static final int KEY_EVENT_RESET_PRESS = 0x40040000;
	private static final int KEY_EVENT_RESET_RELEASE = 0x80040000;
	private static final int PRINTABLE_PRESS_TOKEN_FLAG = 0x20000000;
	
	private static final Map<Integer, byte[]> KEY_MAP;
	private static final byte[] MOD_MAP;

	static {

		// Logical mod-key combinations corresponding to the array index used
		//   in the character decode map
		MOD_MAP = new byte[] {0,1,2,2,3,3,4,4};

		// Key decode map for the following mod-key combinations
		//   0 - No modifiers activated
		//   1 - KeyEvent.VK_CAPS_LOCK
		//   2 - KeyEvent.VK_SHIFT
		//   3 - KeyEvent.VK_CONTROL
		//   4 - KeyEvent.VK_CONTROL & KeyEvent.VK_SHIFT
		KEY_MAP = new HashMap<Integer, byte[]>();

		KEY_MAP.put(KeyEvent.VK_ENTER, new byte[] {0x0D,0x0D,0x0D,0x0D,0x0D});
		KEY_MAP.put(KeyEvent.VK_BACK_SPACE, new byte[] {0x7F,0x7F,0x7F,0x7F,0x7F});
		KEY_MAP.put(KeyEvent.VK_TAB, new byte[] {0x09,0x09,0x09,0x09,0x09});
		KEY_MAP.put(KeyEvent.VK_ESCAPE, new byte[] {0x1B,0x1B,0x1B,0x1B,0x1B});
		KEY_MAP.put(KeyEvent.VK_SPACE, new byte[] {0x20,0x20,0x20,0x20,0x20});

		KEY_MAP.put(KeyEvent.VK_LEFT, new byte[] {0x08,0x08,0x08,0x08,0x08});
		KEY_MAP.put(KeyEvent.VK_UP, new byte[] {0x0B,0x0B,0x0B,0x0B,0x0B});
		KEY_MAP.put(KeyEvent.VK_RIGHT, new byte[] {0x15,0x15,0x15,0x15,0x15});
		KEY_MAP.put(KeyEvent.VK_DOWN, new byte[] {0x0A,0x0A,0x0A,0x0A,0x0A});

		KEY_MAP.put(KeyEvent.VK_COMMA, new byte[] {0x2C,0x2C,0x3C,0x2C,0x3C});
		KEY_MAP.put(KeyEvent.VK_PERIOD, new byte[] {0x2E,0x2E,0x3E,0x2E,0x3E});
		KEY_MAP.put(KeyEvent.VK_SLASH, new byte[] {0x2F,0x2F,0x3F,0x2F,0x3F});
		KEY_MAP.put(KeyEvent.VK_SEMICOLON, new byte[] {0x3B,0x3B,0x3A,0x3B,0x3A});
		KEY_MAP.put(KeyEvent.VK_EQUALS, new byte[] {0x3D,0x3D,0x2B,0x3D,0x2B});
		KEY_MAP.put(KeyEvent.VK_OPEN_BRACKET, new byte[] {0x5B,0x5B,0x7B,0x1B,0x1B});
		KEY_MAP.put(KeyEvent.VK_BACK_SLASH, new byte[] {0x5C,0x5C,0x7C,0x1C,0x7F});
		KEY_MAP.put(KeyEvent.VK_CLOSE_BRACKET, new byte[] {0x5D,0x5D,0x7D,0x1D,0x1D});

		KEY_MAP.put(KeyEvent.VK_MULTIPLY, new byte[] {0x2A,0x2A,0x2A,0x2A,0x2A});
		KEY_MAP.put(KeyEvent.VK_ADD, new byte[] {0x2B,0x2B,0x2B,0x2B,0x2B});
		KEY_MAP.put(KeyEvent.VK_SUBTRACT, new byte[] {0x2D,0x2D,0x2D,0x2D,0x2D});
		KEY_MAP.put(KeyEvent.VK_SEPARATOR, new byte[] {0x7C,0x7C,0x7C,0x7C,0x7C});
		KEY_MAP.put(KeyEvent.VK_DECIMAL, new byte[] {0x2E,0x2E,0x2E,0x2E,0x2E});
		KEY_MAP.put(KeyEvent.VK_DIVIDE, new byte[] {0x2F,0x2F,0x2F,0x2F,0x2F});

		KEY_MAP.put(KeyEvent.VK_BACK_QUOTE, new byte[] {0x60,0x60,0x7E,0x60,0x7E});
		KEY_MAP.put(KeyEvent.VK_QUOTE, new byte[] {0x22,0x22,0x22,0x22,0x22});

		KEY_MAP.put(KeyEvent.VK_KP_UP, new byte[] {0x0B,0x0B,0x0B,0x0B,0x0B});
		KEY_MAP.put(KeyEvent.VK_KP_DOWN, new byte[] {0x0A,0x0A,0x0A,0x0A,0x0A});
		KEY_MAP.put(KeyEvent.VK_KP_LEFT, new byte[] {0x08,0x08,0x08,0x08,0x08});
		KEY_MAP.put(KeyEvent.VK_KP_RIGHT, new byte[] {0x15,0x15,0x15,0x15,0x15});

		KEY_MAP.put(KeyEvent.VK_AMPERSAND, new byte[] {0x26,0x26,0x26,0x26,0x26});
		KEY_MAP.put(KeyEvent.VK_ASTERISK, new byte[] {0x2A,0x2A,0x2A,0x2A,0x2A});
		KEY_MAP.put(KeyEvent.VK_QUOTEDBL, new byte[] {0x22,0x22,0x22,0x22,0x22});
		KEY_MAP.put(KeyEvent.VK_LESS, new byte[] {0x3C,0x3C,0x3C,0x3C,0x3C});
		KEY_MAP.put(KeyEvent.VK_GREATER, new byte[] {0x3E,0x3E,0x3E,0x3E,0x3E});
		KEY_MAP.put(KeyEvent.VK_BRACELEFT, new byte[] {0x7B,0x7B,0x7B,0x7B,0x7B});
		KEY_MAP.put(KeyEvent.VK_BRACERIGHT, new byte[] {0x7D,0x7D,0x7D,0x7D,0x7D});
		KEY_MAP.put(KeyEvent.VK_AT, new byte[] {0x40,0x40,0x40,0x00,0x00});
		KEY_MAP.put(KeyEvent.VK_COLON, new byte[] {0x3A,0x3A,0x3A,0x3A,0x3A});
		KEY_MAP.put(KeyEvent.VK_DOLLAR, new byte[] {0x24,0x24,0x24,0x24,0x24});
		KEY_MAP.put(KeyEvent.VK_CIRCUMFLEX, new byte[] {0x5E,0x5E,0x5E,0x1E,0x1E});
		KEY_MAP.put(KeyEvent.VK_EXCLAMATION_MARK, new byte[] {0x21,0x21,0x21,0x21,0x21});
		KEY_MAP.put(KeyEvent.VK_LEFT_PARENTHESIS, new byte[] {0x28,0x28,0x28,0x28,0x28});
		KEY_MAP.put(KeyEvent.VK_NUMBER_SIGN, new byte[] {0x23,0x23,0x23,0x23,0x23});
		KEY_MAP.put(KeyEvent.VK_MINUS, new byte[] {0x2D,0x2D,0x5F,0x2D,0x1F});
		KEY_MAP.put(KeyEvent.VK_PLUS, new byte[] {0x2B,0x2B,0x2B,0x2B,0x2B});
		KEY_MAP.put(KeyEvent.VK_RIGHT_PARENTHESIS, new byte[] {0x29,0x29,0x29,0x29,0x29});
		KEY_MAP.put(KeyEvent.VK_UNDERSCORE, new byte[] {0x5F,0x5F,0x5F,0x5F,0x5F});

		KEY_MAP.put(KeyEvent.VK_DEAD_GRAVE, new byte[] {0x60,0x60,0x7E,0x60,0x7E});
		KEY_MAP.put(KeyEvent.VK_DEAD_ACUTE, new byte[] {0x65,0x45,0x45,0x05,0x05});
		KEY_MAP.put(KeyEvent.VK_DEAD_CIRCUMFLEX, new byte[] {0x69,0x49,0x49,0x09,0x09});
		KEY_MAP.put(KeyEvent.VK_DEAD_TILDE, new byte[] {0x6E,0x4E,0x4E,0x0E,0x0E});
		KEY_MAP.put(KeyEvent.VK_DEAD_DIAERESIS, new byte[] {0x75,0x55,0x55,0x15,0x15});

		KEY_MAP.put(KeyEvent.VK_0, new byte[] {0x30,0x30,0x29,0x30,0x30});
		KEY_MAP.put(KeyEvent.VK_1, new byte[] {0x31,0x31,0x21,0x31,0x31});
		KEY_MAP.put(KeyEvent.VK_2, new byte[] {0x32,0x32,0x40,0x00,0x00});
		KEY_MAP.put(KeyEvent.VK_3, new byte[] {0x33,0x33,0x23,0x33,0x33});
		KEY_MAP.put(KeyEvent.VK_4, new byte[] {0x34,0x34,0x24,0x34,0x34});
		KEY_MAP.put(KeyEvent.VK_5, new byte[] {0x35,0x35,0x25,0x35,0x35});
		KEY_MAP.put(KeyEvent.VK_6, new byte[] {0x36,0x36,0x5E,0x1E,0x1E});
		KEY_MAP.put(KeyEvent.VK_7, new byte[] {0x37,0x37,0x26,0x37,0x37});
		KEY_MAP.put(KeyEvent.VK_8, new byte[] {0x38,0x38,0x2A,0x38,0x38});
		KEY_MAP.put(KeyEvent.VK_9, new byte[] {0x39,0x39,0x28,0x39,0x39});

		KEY_MAP.put(KeyEvent.VK_NUMPAD0, new byte[] {0x30,0x30,0x30,0x30,0x30});
		KEY_MAP.put(KeyEvent.VK_NUMPAD1, new byte[] {0x31,0x31,0x31,0x31,0x31});
		KEY_MAP.put(KeyEvent.VK_NUMPAD2, new byte[] {0x32,0x32,0x32,0x32,0x32});
		KEY_MAP.put(KeyEvent.VK_NUMPAD3, new byte[] {0x33,0x33,0x33,0x33,0x33});
		KEY_MAP.put(KeyEvent.VK_NUMPAD4, new byte[] {0x34,0x34,0x34,0x34,0x34});
		KEY_MAP.put(KeyEvent.VK_NUMPAD5, new byte[] {0x35,0x35,0x35,0x35,0x35});
		KEY_MAP.put(KeyEvent.VK_NUMPAD6, new byte[] {0x36,0x36,0x36,0x36,0x36});
		KEY_MAP.put(KeyEvent.VK_NUMPAD7, new byte[] {0x37,0x37,0x37,0x37,0x37});
		KEY_MAP.put(KeyEvent.VK_NUMPAD8, new byte[] {0x38,0x38,0x38,0x38,0x38});
		KEY_MAP.put(KeyEvent.VK_NUMPAD9, new byte[] {0x39,0x39,0x39,0x39,0x39});

		KEY_MAP.put(KeyEvent.VK_A, new byte[] {0x61,0x41,0x41,0x01,0x01});
		KEY_MAP.put(KeyEvent.VK_B, new byte[] {0x62,0x42,0x42,0x02,0x02});
		KEY_MAP.put(KeyEvent.VK_C, new byte[] {0x63,0x43,0x43,0x03,0x03});
		KEY_MAP.put(KeyEvent.VK_D, new byte[] {0x64,0x44,0x44,0x04,0x04});
		KEY_MAP.put(KeyEvent.VK_E, new byte[] {0x65,0x45,0x45,0x05,0x05});
		KEY_MAP.put(KeyEvent.VK_F, new byte[] {0x66,0x46,0x46,0x06,0x06});
		KEY_MAP.put(KeyEvent.VK_G, new byte[] {0x67,0x47,0x47,0x07,0x07});
		KEY_MAP.put(KeyEvent.VK_H, new byte[] {0x68,0x48,0x48,0x08,0x08});
		KEY_MAP.put(KeyEvent.VK_I, new byte[] {0x69,0x49,0x49,0x09,0x09});
		KEY_MAP.put(KeyEvent.VK_J, new byte[] {0x6A,0x4A,0x4A,0x0A,0x0A});
		KEY_MAP.put(KeyEvent.VK_K, new byte[] {0x6B,0x4B,0x4B,0x0B,0x0B});
		KEY_MAP.put(KeyEvent.VK_L, new byte[] {0x6C,0x4C,0x4C,0x0C,0x0C});
		KEY_MAP.put(KeyEvent.VK_M, new byte[] {0x6D,0x4D,0x4D,0x0D,0x0D});
		KEY_MAP.put(KeyEvent.VK_N, new byte[] {0x6E,0x4E,0x4E,0x0E,0x0E});
		KEY_MAP.put(KeyEvent.VK_O, new byte[] {0x6F,0x4F,0x4F,0x0F,0x0F});
		KEY_MAP.put(KeyEvent.VK_P, new byte[] {0x70,0x50,0x50,0x10,0x10});
		KEY_MAP.put(KeyEvent.VK_Q, new byte[] {0x71,0x51,0x51,0x11,0x11});
		KEY_MAP.put(KeyEvent.VK_R, new byte[] {0x72,0x52,0x52,0x12,0x12});
		KEY_MAP.put(KeyEvent.VK_S, new byte[] {0x73,0x53,0x53,0x13,0x13});
		KEY_MAP.put(KeyEvent.VK_T, new byte[] {0x74,0x54,0x54,0x14,0x14});
		KEY_MAP.put(KeyEvent.VK_U, new byte[] {0x75,0x55,0x55,0x15,0x15});
		KEY_MAP.put(KeyEvent.VK_V, new byte[] {0x76,0x56,0x56,0x16,0x16});
		KEY_MAP.put(KeyEvent.VK_W, new byte[] {0x77,0x57,0x57,0x17,0x17});
		KEY_MAP.put(KeyEvent.VK_X, new byte[] {0x78,0x58,0x58,0x18,0x18});
		KEY_MAP.put(KeyEvent.VK_Y, new byte[] {0x79,0x59,0x59,0x19,0x19});
		KEY_MAP.put(KeyEvent.VK_Z, new byte[] {0x7A,0x5A,0x5A,0x1A,0x1A});

	}
	
	public KeyboardIIe( long unitsPerCycle, Cpu65c02 cpu ) throws HardwareException {
		super(unitsPerCycle);
		coldReset();
		this.cpu = cpu;
		capsLockState = false;
		applyCapsLockState();
	}

	public static void setKeyLoggingEnabled(boolean enabled) {
		keyLoggingEnabled = enabled;
	}

	@Override
	public void keyPressed( KeyEvent event ) {
		handleKeyPressed(event.getKeyCode(), event.getModifiers(), event.getKeyChar(),
				event.isShiftDown(), event.isControlDown(), event.isAltDown(), event.isMetaDown());
	}

	public void keyPressedRaw(int keyIndex, int keyModifiers, char keyChar,
			boolean shiftDown, boolean ctrlDown, boolean altDown, boolean metaDown) {
		handleKeyPressed(keyIndex, keyModifiers, keyChar, shiftDown, ctrlDown, altDown, metaDown);
	}

	private void handleKeyPressed(int keyIndex, int keyModifiers, char keyChar,
			boolean shiftDown, boolean ctrlDown, boolean altDown, boolean metaDown) {
		logKeyProbe("pressed", keyIndex, keyChar, shiftDown, ctrlDown, altDown, metaDown, keyModifiers);
		
		//System.out.println(KeyEvent.getKeyText(keyIndex)+" "+keyIndex+" "+event.getModifiers());

		// Maintain caps-lock state internally; host modifier masks are not reliable
		// for lock-key state across SDL/AWT bridges.
		if( keyIndex==KeyEvent.VK_CAPS_LOCK ) {
			capsLockState = !capsLockState;
			applyCapsLockState();
			return;
		}
		
		switch( keyIndex ) {

		// Modifiers
		case KeyEvent.VK_SHIFT:	       modifierSet |= KEY_MASK_SHIFT; return;
		case KeyEvent.VK_CONTROL:      modifierSet |= KEY_MASK_CTRL; return;
		case KeyEvent.VK_ALT:          appleKey = true; return;
		case KeyEvent.VK_META:         optionKey = true; return;

		// Function keys
		case KeyEvent.VK_F1:           pushKeyEvent(KEY_MASK_F1); break;
		case KeyEvent.VK_F2:           pushKeyEvent(KEY_MASK_F2); break;
		case KeyEvent.VK_F3:           pushKeyEvent(KEY_MASK_F3); break;
		case KeyEvent.VK_F4:           pushKeyEvent(KEY_MASK_F4); break;
		case KeyEvent.VK_F5:           pushKeyEvent(KEY_MASK_F5); break;
		case KeyEvent.VK_F6:           pushKeyEvent(KEY_MASK_F6); break;
		case KeyEvent.VK_F7:           pushKeyEvent(KEY_MASK_F7); break;
		case KeyEvent.VK_F8:           pushKeyEvent(KEY_MASK_F8); break;
		case KeyEvent.VK_F9:           pushKeyEvent(KEY_MASK_F9); break;
		case KeyEvent.VK_F10:          pushKeyEvent(KEY_MASK_F10); break;
		case KeyEvent.VK_F11:
			// Ignore Ctrl+F11 to avoid accidental non-reset hotkeys when users intend Ctrl+F12.
			if( ctrlDown || (modifierSet&KEY_MASK_CTRL)!=0 )
				return;
			break;
		case KeyEvent.VK_F12:
			if( (modifierSet&KEY_MASK_CTRL)!=0 && !isHalted ) {
				clearQueuedKeys();
				isHalted = true;
				cpu.setInterruptPending(Cpu65c02.INTERRUPT_HLT);
			}
			break;
		case KeyEvent.VK_INSERT:
		case KeyEvent.VK_HELP:
			pushKeyEvent(KEY_MASK_F12, shiftDown ? KEY_MASK_SHIFT:0);
			break;

		default:
			if( pasteInputSuppressed ) {
				// If user starts typing while paste is pending, prefer live input
				// and drop queued paste to avoid a stuck "no keys work" state.
				clearQueuedKeys();
				clearPendingKeyInputQueue();
				clearHeldKeyState();
			}
			// Keep control/navigation on keycode path, but use layout-resolved
			// printable chars for locale-correct text input timing/repeat.
			if( (modifierSet&KEY_MASK_CTRL)==0 && !isNumpadKey(keyIndex) ) {
				boolean effectiveShiftDown = shiftDown || (modifierSet&KEY_MASK_SHIFT)!=0;
				Integer printableChar = mapPrintableChar(keyChar, effectiveShiftDown);
				if( printableChar!=null ) {
					int printablePressToken = toPrintablePressToken(keyIndex);
					if( !isKeyPressed(printablePressToken) ) {
						if( keyQueue.size()==0 )
							keyCode = (byte) (0x80|printableChar.intValue());
						keyPressed.add(printablePressToken);
						keyCount++;
						delayCount = PRE_REPEAT_FLOP_COUNT;
					}
					return;
				}
			}
			byte[] keyCodeArray = KEY_MAP.get(keyIndex);
			modIndex = MOD_MAP[modifierSet&(KEY_MASK_CAPS|KEY_MASK_SHIFT|KEY_MASK_CTRL)];
			if( keyCodeArray!=null ) {
				if( !isKeyPressed(keyCodeArray[0]) ) {
					if( keyQueue.size()==0 )
						keyCode = (byte) (0x80|keyCodeArray[modIndex]);
					keyPressed.add((int) keyCodeArray[0]);
					keyCount++;
					delayCount = PRE_REPEAT_FLOP_COUNT;
				}
			}
			
		}
		
	}
	
	private int pushKeyEvent( int eventKey ) {
		return pushKeyEvent(eventKey, 0);
	}

	private int pushKeyEvent( int eventKey, int forcedModifiers ) {
		if( !keyPressed.contains(eventKey) )
			return pushPressedKeyEvent(eventKey, forcedModifiers);
		return -1;
	}

	private int pushPressedKeyEvent( int eventKey ) {
		return pushPressedKeyEvent(eventKey, 0);
	}

	private int pushPressedKeyEvent( int eventKey, int forcedModifiers ) {
		keyPressed.add(eventKey);
		functionKeySet |= eventKey;
		int effectiveModifiers = modifierSet|forcedModifiers;
		keyEventQueue.add(functionKeySet|effectiveModifiers);
		return functionKeySet|effectiveModifiers;
	}

	private void endPressedKeyEvent( int eventKey ) {
		keyPressed.remove(eventKey);
		functionKeySet &= ~eventKey;
	}
	
	public Integer nextKeyEvent() {
		return keyEventQueue.poll();
	}

	@Override
	public void keyReleased( KeyEvent e ) {
		handleKeyReleased(e.getKeyCode(), e.getModifiers(), e.getKeyChar(),
				e.isShiftDown(), e.isControlDown(), e.isAltDown(), e.isMetaDown());
	}

	public void keyReleasedRaw(int keyIndex, int keyModifiers, char keyChar,
			boolean shiftDown, boolean ctrlDown, boolean altDown, boolean metaDown) {
		handleKeyReleased(keyIndex, keyModifiers, keyChar, shiftDown, ctrlDown, altDown, metaDown);
	}

	private void handleKeyReleased(int keyIndex, int keyModifiers, char keyChar,
			boolean shiftDown, boolean ctrlDown, boolean altDown, boolean metaDown) {
		logKeyProbe("released", keyIndex, keyChar, shiftDown, ctrlDown, altDown, metaDown, keyModifiers);
		
		switch( keyIndex ) {

		// Modifiers
		case KeyEvent.VK_SHIFT:        modifierSet &= ~KEY_MASK_SHIFT; break;
		case KeyEvent.VK_CONTROL:
			modifierSet &= ~KEY_MASK_CTRL;
			if( isHalted ) {
				cpu.setInterruptPending(Cpu65c02.INTERRUPT_RES);
				isHalted = false;
			}
			break;
		case KeyEvent.VK_ALT:          appleKey = false; break;
		case KeyEvent.VK_META:         optionKey = false; break;

		// Function keys
		case KeyEvent.VK_F1:           endPressedKeyEvent(KEY_MASK_F1); break;
		case KeyEvent.VK_F2:           endPressedKeyEvent(KEY_MASK_F2); break;
		case KeyEvent.VK_F3:           endPressedKeyEvent(KEY_MASK_F3); break;
		case KeyEvent.VK_F4:           endPressedKeyEvent(KEY_MASK_F4); break;
		case KeyEvent.VK_F5:           endPressedKeyEvent(KEY_MASK_F5); break;
		case KeyEvent.VK_F6:           endPressedKeyEvent(KEY_MASK_F6); break;
		case KeyEvent.VK_F7:           endPressedKeyEvent(KEY_MASK_F7); break;
		case KeyEvent.VK_F8:           endPressedKeyEvent(KEY_MASK_F8); break;
		case KeyEvent.VK_F9:           endPressedKeyEvent(KEY_MASK_F9); break;
		case KeyEvent.VK_F10:          endPressedKeyEvent(KEY_MASK_F10); break;
		case KeyEvent.VK_F11:
			if( ctrlDown || (modifierSet&KEY_MASK_CTRL)!=0 )
				return;
			break;
		case KeyEvent.VK_F12:
			if( isHalted ) {
				cpu.setInterruptPending(Cpu65c02.INTERRUPT_RES);
				isHalted = false;
			}
			break;
		case KeyEvent.VK_INSERT:
		case KeyEvent.VK_HELP:
			endPressedKeyEvent(KEY_MASK_F12);
			break;

		default:
			int printablePressToken = toPrintablePressToken(keyIndex);
			if( isKeyPressed(printablePressToken) ) {
				keyPressed.remove(printablePressToken);
				keyCount--;
				if( keyCount==0 )
					delayCount = 0;
				return;
			}
			byte[] keyCodeArray = KEY_MAP.get(keyIndex);
			if( keyCodeArray==null )
				return;
			int newKeyCode = keyCodeArray[0];
			if( isKeyPressed(newKeyCode) ) {
				keyPressed.remove(newKeyCode);
				keyCount--;
				if( keyCount==0 )
					delayCount = 0;
			}
			break;
		
		}

	}

	private static int toPrintablePressToken(int keyIndex) {
		return PRINTABLE_PRESS_TOKEN_FLAG | (keyIndex&0x0000ffff);
	}

	private static boolean isNumpadKey(int keyIndex) {
		switch( keyIndex ) {
		case KeyEvent.VK_NUMPAD0:
		case KeyEvent.VK_NUMPAD1:
		case KeyEvent.VK_NUMPAD2:
		case KeyEvent.VK_NUMPAD3:
		case KeyEvent.VK_NUMPAD4:
		case KeyEvent.VK_NUMPAD5:
		case KeyEvent.VK_NUMPAD6:
		case KeyEvent.VK_NUMPAD7:
		case KeyEvent.VK_NUMPAD8:
		case KeyEvent.VK_NUMPAD9:
		case KeyEvent.VK_DECIMAL:
		case KeyEvent.VK_DIVIDE:
		case KeyEvent.VK_MULTIPLY:
		case KeyEvent.VK_SUBTRACT:
		case KeyEvent.VK_ADD:
			return true;
		default:
			return false;
		}
	}

	private Integer mapPrintableChar(char keyChar, boolean shiftDown) {
		if( keyChar==KeyEvent.CHAR_UNDEFINED )
			return null;
		if( keyChar==0x0a )
			return 0x0d;
		if( Character.isLetter(keyChar) ) {
			boolean capsDown = (modifierSet&KEY_MASK_CAPS)!=0;
			char normalized = (capsDown ^ shiftDown) ? Character.toUpperCase(keyChar) : Character.toLowerCase(keyChar);
			return (int) normalized;
		}
		if( shiftDown && keyChar>='0' && keyChar<='9' ) {
			switch( keyChar ) {
			case '1': return (int) '!';
			case '2': return (int) '@';
			case '3': return (int) '#';
			case '4': return (int) '$';
			case '5': return (int) '%';
			case '6': return (int) '^';
			case '7': return (int) '&';
			case '8': return (int) '*';
			case '9': return (int) '(';
			case '0': return (int) ')';
			default: break;
			}
		}
		if( shiftDown ) {
			switch( keyChar ) {
			case '-': return (int) '_';
			case '=': return (int) '+';
			case '[': return (int) '{';
			case ']': return (int) '}';
			case '\\': return (int) '|';
			case ';': return (int) ':';
			case '\'': return (int) '"';
			case ',': return (int) '<';
			case '.': return (int) '>';
			case '/': return (int) '?';
			case '`': return (int) '~';
			default: break;
			}
		}
		if( keyChar>=0x20 && keyChar<=0x7e )
			return (int) keyChar;
		return null;
	}

	private boolean isConsumed;

	private void clearQueuedKeys() {
		keyQueue.clear();
		pasteMarkerQueue.clear();
		pendingPastedKeys = 0;
		pasteInputSuppressed = false;
	}

	private void clearPendingKeyInputQueue() {
		keyEventQueue.clear();
	}

	private void clearHeldKeyState() {
		keyPressed.clear();
		functionKeySet = 0;
		keyCount = 0;
		delayCount = 0;
	}

	private void pushKeyCodeInternal(int i, boolean fromPaste) {
		keyQueue.add((byte) i);
		pasteMarkerQueue.add(Boolean.valueOf(fromPaste));
		if( fromPaste ) {
			pasteInputSuppressed = true;
			pendingPastedKeys++;
		}
		queuedKeyCount++;
	}
	
	public void toggleKeyQueue( boolean consume ) {
		if( consume ) {
			// C000 read: present next queued key as a fresh strobe.
			if( !isConsumed && keyQueue.size()>0 ) {
				keyCode = (byte) (keyQueue.poll()|0x80);
				boolean consumedPasteKey = Boolean.TRUE.equals(pasteMarkerQueue.poll());
				if( consumedPasteKey ) {
					pendingPastedKeys--;
					if( pendingPastedKeys<=0 ) {
						pendingPastedKeys = 0;
						if( pasteInputSuppressed ) {
							pasteInputSuppressed = false;
							clearPendingKeyInputQueue();
							clearHeldKeyState();
						}
					}
				}
				consumedQueuedKeyCount++;
				isConsumed = true;
			}
		}
		else if( isConsumed ) {
			// C010 read/write: clear strobe until next key is consumed from queue.
			keyCode &= 0x7f;
			isConsumed = false;
		}

	}

	@Override
	public int getHeldKeyCode() {
		if( keyQueue.size()>0 )
			return keyCode&0x7f;
		keyCode &= 0x7f;
		return keyCode|(keyCount>0 ? 0x80:0x00);
	}
	
	@Override
	public int getTypedKeyCode() {
		return keyCode;
	}

	public void pushKeyCode( int i ) {
		pushKeyCodeInternal(i, false);
	}

	public void queuePasteText(String text) {
		if( text==null || text.isEmpty() )
			return;
		for( int i = 0; i<text.length(); i++ ) {
			char c = text.charAt(i);
			if( c==0x0d ) {
				// Treat CRLF as a single return.
				if( i+1<text.length() && text.charAt(i+1)==0x0a )
					i++;
				pushKeyCodeInternal(0x0d, true);
				continue;
			}
			pushKeyCodeInternal(c==0x0a ? 0x0d:c, true);
		}
	}

	private static String trimTrailingClipboardNewline(String text) {
		if( text==null || text.isEmpty() )
			return text;
		// Keep one intentional trailing newline, but drop one extra duplicate.
		if( text.endsWith("\r\n\r\n") )
			return text.substring(0, text.length()-2);
		if( text.endsWith("\n\n") || text.endsWith("\r\r") )
			return text.substring(0, text.length()-1);
		return text;
	}

	public long getQueuedKeyCount() {
		return queuedKeyCount;
	}

	public long getConsumedQueuedKeyCount() {
		return consumedQueuedKeyCount;
	}

	public int getQueuedKeyDepth() {
		return keyQueue.size();
	}

	public boolean isOptionKey() {
		return optionKey;
	}

	public boolean isAppleKey() {
		return appleKey;
	}

	public boolean isShiftKey() {
		return (modifierSet&KEY_MASK_SHIFT)!=0;
	}
	
	@Override
	public void cycle() throws HardwareException {

		incSleepCycles(1);
		cycleCount++;
		
		// Check for key-repeat
		if( delayCount>0 && (keyCode&0x80)==0 && (cycleCount&(FLOP_DELAY_CYCLES-1))==0 && keyQueue.size()==0 ) {
			delayCount--;
			if( delayCount==0 ) {
				delayCount = 1;
				keyCode |= 0x80;
			}
		}
		
		Integer keyEvent = nextKeyEvent();
		if( keyEvent==null )
			return;
		
		//System.out.println(keyEvent);

		switch( keyEvent ) {

		case KEY_EVENT_RESET_PRESS:
			cpu.setInterruptPending(Cpu65c02.INTERRUPT_HLT);
			clearQueuedKeys();
			break;

		case KEY_EVENT_RESET_RELEASE:
			cpu.setInterruptPending(Cpu65c02.INTERRUPT_RES);
			break;

		case KEY_MASK_F12:
			clearQueuedKeys();
			break;
			
		case KEY_MASK_F12|KEY_MASK_SHIFT:
			requestClipboardPaste();
			break;
			
		}

	}

	@Override
	public void coldReset() throws HardwareException {
		keyEventQueue = new ConcurrentLinkedQueue<>();
		keyQueue = new LinkedList<>();
		pasteMarkerQueue = new LinkedList<>();
		isHalted = false;
		cycleCount = 0;
		delayCount = 0;
		queuedKeyCount = 0L;
		consumedQueuedKeyCount = 0L;
		pendingPastedKeys = 0;
		pasteInputSuppressed = false;
		capsLockState = false;
		applyCapsLockState();
	}

	private void applyCapsLockState() {
		if( capsLockState )
			modifierSet |= KEY_MASK_CAPS;
		else
			modifierSet &= ~KEY_MASK_CAPS;
	}

	private static void logKeyProbe(String phase, int keyCode, char keyChar,
			boolean shiftDown, boolean ctrlDown, boolean altDown, boolean metaDown, int modifiersEx) {
		if( !keyLoggingEnabled )
			return;
		if( keyCode!=KeyEvent.VK_INSERT && keyCode!=KeyEvent.VK_F11 && keyCode!=KeyEvent.VK_F12 && keyCode!=KeyEvent.VK_HELP )
			return;
		String charDesc = keyChar==KeyEvent.CHAR_UNDEFINED ? "undef" : Integer.toString((int) keyChar);
		System.err.println("[debug] key_probe phase="+phase+
				" keyCode="+keyCode+
				" keyText="+KeyEvent.getKeyText(keyCode)+
				" keyChar="+charDesc+
				" shiftDown="+shiftDown+
				" ctrlDown="+ctrlDown+
				" altDown="+altDown+
				" metaDown="+metaDown+
				" modifiersEx=0x"+Integer.toHexString(modifiersEx));
	}

	private void requestClipboardPaste() {
		if( keyQueue.size()!=0 )
			return;
		String text = readClipboardTextFromSdl();
		if( keyLoggingEnabled ) {
			System.err.println("[debug] paste_request source=shift_insert"+
					" clipboardTextPresent="+(text!=null && !text.isEmpty())+
					" queueDepth="+keyQueue.size());
		}
		if( text!=null )
			queuePasteText(trimTrailingClipboardNewline(text));
	}

	private String readClipboardTextFromSdl() {
		try {
			if( !SDLClipboard.SDL_HasClipboardText() )
				return null;
			String text = SDLClipboard.SDL_GetClipboardText();
			if( text==null || text.isEmpty() )
				return null;
			return text;
		}
		catch( Throwable ignored ) {
			// Keep paste path resilient if SDL clipboard fails.
			return null;
		}
	}

}
