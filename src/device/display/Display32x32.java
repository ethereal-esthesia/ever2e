package device.display;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLRender;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_FRect;
import org.lwjgl.sdl.SDL_KeyboardEvent;
import org.lwjgl.system.MemoryUtil;

import core.exception.HardwareException;
import core.memory.memory8.MemoryBus8;
import device.keyboard.AwtInputMapper;
import device.keyboard.KeyboardIIe;

public class Display32x32 extends DisplayWindow {

	private static final int PIXEL_MULT = 16;
	private static final int WIDTH = 32;
	private static final int HEIGHT = 32;
	private static final int WINDOW_WIDTH = WIDTH*PIXEL_MULT;
	private static final int WINDOW_HEIGHT = HEIGHT*PIXEL_MULT;

	private static final int[] PAL = new int[] {
		0xFF000000, 0xFFFFFFFF, 0xFF850005, 0xFFAFFFEE,
		0xFFCA36CC, 0xFF26CF55, 0xFF1400A9, 0xFFEDF17A,
		0xFFDA8958, 0xFF644503, 0xFFFB7679, 0xFF333333,
		0xFF777777, 0xFFACFF68, 0xFF2D81FE, 0xFFBBBBBB
	};

	private final MemoryBus8 memoryBus;
	private final KeyboardIIe keyboard;
	private final int[] pixels;
	private final ByteBuffer textureBytes;
	private final IntBuffer textureInts;

	private long sdlWindow;
	private long sdlRenderer;
	private long sdlTexture;

	public Display32x32(MemoryBus8 memoryBus, KeyboardIIe keyboard, long unitsPerCycle) throws HardwareException {
		super(unitsPerCycle);
		this.memoryBus = memoryBus;
		this.keyboard = keyboard;
		this.pixels = new int[WIDTH*HEIGHT];
		this.textureBytes = BufferUtils.createByteBuffer(WIDTH*HEIGHT*4);
		this.textureInts = textureBytes.asIntBuffer();
		initSdl();
	}

	private void initSdl() throws HardwareException {
		if( !SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO | SDLInit.SDL_INIT_EVENTS) )
			throw new HardwareException("Unable to initialize SDL: " + SDLError.SDL_GetError());
		sdlWindow = SDLVideo.SDL_CreateWindow("32x32x16@0200h 65C02 Emulator", WINDOW_WIDTH, WINDOW_HEIGHT, SDLVideo.SDL_WINDOW_RESIZABLE);
		if( sdlWindow==0L ) {
			SDLInit.SDL_Quit();
			throw new HardwareException("Unable to create SDL window: " + SDLError.SDL_GetError());
		}
		sdlRenderer = SDLRender.nSDL_CreateRenderer(sdlWindow, 0L);
		if( sdlRenderer==0L ) {
			SDLVideo.SDL_DestroyWindow(sdlWindow);
			sdlWindow = 0L;
			SDLInit.SDL_Quit();
			throw new HardwareException("Unable to create SDL renderer: " + SDLError.SDL_GetError());
		}
		sdlTexture = SDLRender.nSDL_CreateTexture(
				sdlRenderer,
				SDLPixels.SDL_PIXELFORMAT_ARGB8888,
				SDLRender.SDL_TEXTUREACCESS_STREAMING,
				WIDTH,
				HEIGHT);
		if( sdlTexture==0L ) {
			SDLRender.SDL_DestroyRenderer(sdlRenderer);
			sdlRenderer = 0L;
			SDLVideo.SDL_DestroyWindow(sdlWindow);
			sdlWindow = 0L;
			SDLInit.SDL_Quit();
			throw new HardwareException("Unable to create SDL texture: " + SDLError.SDL_GetError());
		}
		SDLVideo.SDL_ShowWindow(sdlWindow);
		SDLVideo.SDL_RaiseWindow(sdlWindow);
	}

	private void closeWindow() {
		if( sdlTexture!=0L ) {
			SDLRender.nSDL_DestroyTexture(sdlTexture);
			sdlTexture = 0L;
		}
		if( sdlRenderer!=0L ) {
			SDLRender.SDL_DestroyRenderer(sdlRenderer);
			sdlRenderer = 0L;
		}
		if( sdlWindow!=0L ) {
			SDLVideo.SDL_DestroyWindow(sdlWindow);
			sdlWindow = 0L;
		}
		SDLInit.SDL_Quit();
	}

	private void processKeyboardEvent(SDL_KeyboardEvent keyEvent, boolean pressed) {
		int scancode = keyEvent.scancode();
		int key = keyEvent.key();
		short mods = keyEvent.mod();
		int emuKey = AwtInputMapper.toAwtKeyCodeFromSdlScancode(scancode);
		if( emuKey==AwtInputMapper.KEY_UNDEFINED )
			emuKey = AwtInputMapper.toAwtKeyCodeFromSdlKeycode(key);
		if( emuKey==AwtInputMapper.KEY_UNDEFINED )
			return;
		char keyChar = AwtInputMapper.mapSdlKeyChar(key);
		boolean shiftDown = (mods&SDLKeycode.SDL_KMOD_SHIFT)!=0;
		boolean ctrlDown = (mods&SDLKeycode.SDL_KMOD_CTRL)!=0;
		boolean altDown = (mods&SDLKeycode.SDL_KMOD_ALT)!=0;
		boolean metaDown = (mods&SDLKeycode.SDL_KMOD_GUI)!=0;
		int emuModifiers = AwtInputMapper.toAwtModifiersFromSdl(mods);
		if( pressed )
			keyboard.keyPressedRaw(emuKey, emuModifiers, keyChar, shiftDown, ctrlDown, altDown, metaDown);
		else
			keyboard.keyReleasedRaw(emuKey, emuModifiers, keyChar, shiftDown, ctrlDown, altDown, metaDown);
	}

	private void pollSdlEvents() {
		try( SDL_Event event = SDL_Event.malloc() ) {
			while( SDLEvents.SDL_PollEvent(event) ) {
				int type = event.type();
				if( type==SDLEvents.SDL_EVENT_QUIT || type==SDLEvents.SDL_EVENT_WINDOW_CLOSE_REQUESTED ) {
					closeWindow();
					System.exit(0);
					return;
				}
				if( type==SDLEvents.SDL_EVENT_KEY_DOWN || type==SDLEvents.SDL_EVENT_KEY_UP )
					processKeyboardEvent(event.key(), type==SDLEvents.SDL_EVENT_KEY_DOWN);
			}
		}
	}

	private void updatePixelsFromMemory() {
		for( int y = 0; y<HEIGHT; y++ ) {
			for( int x = 0; x<WIDTH; x++ ) {
				int addr = getAddressLo(y, x);
				pixels[(y*WIDTH)+x] = PAL[memoryBus.getByte(addr)&0x0f];
			}
		}
	}

	private void render() {
		if( sdlWindow==0L || sdlRenderer==0L || sdlTexture==0L )
			return;
		updatePixelsFromMemory();
		textureInts.clear();
		textureInts.put(pixels);
		textureInts.flip();
		SDLRender.nSDL_UpdateTexture(sdlTexture, 0L, MemoryUtil.memAddress(textureBytes), WIDTH*4);
		IntBuffer fbWidth = BufferUtils.createIntBuffer(1);
		IntBuffer fbHeight = BufferUtils.createIntBuffer(1);
		SDLVideo.SDL_GetWindowSizeInPixels(sdlWindow, fbWidth, fbHeight);
		SDLRender.SDL_SetRenderDrawColor(sdlRenderer, (byte) 0, (byte) 0, (byte) 0, (byte) 0xff);
		SDLRender.SDL_RenderClear(sdlRenderer);
		try( SDL_FRect dst = SDL_FRect.calloc() ) {
			dst.x(0f);
			dst.y(0f);
			dst.w((float) fbWidth.get(0));
			dst.h((float) fbHeight.get(0));
			SDLRender.nSDL_RenderTexture(sdlRenderer, sdlTexture, 0L, dst.address());
		}
		SDLRender.SDL_RenderPresent(sdlRenderer);
	}

	@Override
	public void cycle() throws HardwareException {
		incSleepCycles(1);
		pollSdlEvents();
		render();
	}

	public static int getAddressLo(int scanline, int offset) {
		return 0x0200+(scanline<<5)+offset;
	}

	@Override
	public void coldReset() throws HardwareException {
	}
}
