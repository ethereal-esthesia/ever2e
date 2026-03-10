package tools;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLScancode;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDLRender;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_FRect;
import org.lwjgl.sdl.SDL_KeyboardEvent;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.system.MemoryUtil;

/**
 * Minimal SDL3 text/input probe for macOS IME/Caps HUD behavior.
 */
public final class SdlImeProbe {
	private SdlImeProbe() {
	}

	private static final class MacPresentationLock {
		private static final boolean IS_MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");
		private static final long NS_APP_PRESENTATION_AUTO_HIDE_DOCK = 1L << 0;
		private static final long NS_APP_PRESENTATION_DISABLE_PROCESS_SWITCHING = 1L << 5;

		private final boolean available;
		private final Function objcMsgSend;
		private final long nsApplicationClass;
		private final long selSharedApplication;
		private final long selSetPresentationOptions;
		private long cachedAppInstance;
		private boolean disabled;

		MacPresentationLock() {
			if( !IS_MAC ) {
				available = false;
				objcMsgSend = null;
				nsApplicationClass = 0L;
				selSharedApplication = 0L;
				selSetPresentationOptions = 0L;
				return;
			}
			try {
				NativeLibrary objc = NativeLibrary.getInstance("objc");
				Function objcGetClass = objc.getFunction("objc_getClass");
				Function selRegisterName = objc.getFunction("sel_registerName");
				objcMsgSend = objc.getFunction("objc_msgSend");
				nsApplicationClass = objcGetClass.invokeLong(new Object[] {"NSApplication"});
				selSharedApplication = selRegisterName.invokeLong(new Object[] {"sharedApplication"});
				selSetPresentationOptions = selRegisterName.invokeLong(new Object[] {"setPresentationOptions:"});
				available = nsApplicationClass!=0L && selSharedApplication!=0L && selSetPresentationOptions!=0L;
			}
			catch( Throwable t ) {
				throw new RuntimeException("Failed to initialize macOS presentation lock", t);
			}
		}

		boolean isAvailable() {
			return available;
		}

		void setDisableProcessSwitching(boolean enabled, String source) {
			if( !available )
				return;
			if( disabled==enabled )
				return;
			long app = appInstance();
			if( app==0L )
				return;
			long options = enabled
					? (NS_APP_PRESENTATION_AUTO_HIDE_DOCK | NS_APP_PRESENTATION_DISABLE_PROCESS_SWITCHING)
					: 0L;
			objcMsgSend.invokeVoid(new Object[] {app, selSetPresentationOptions, options});
			disabled = enabled;
			System.out.println("[probe] mac_presentation source=" + source
					+ " disableProcessSwitching=" + disabled
					+ " options=0x" + Long.toHexString(options));
		}

		private long appInstance() {
			if( cachedAppInstance!=0L )
				return cachedAppInstance;
			cachedAppInstance = objcMsgSend.invokeLong(new Object[] {nsApplicationClass, selSharedApplication});
			return cachedAppInstance;
		}
	}

	private static void trace(boolean enabled, String msg) {
		if( enabled )
			System.out.println("[sdl-trace] " + msg);
	}

	public static void main(String[] args) {
		boolean mouseDebug = hasArg(args, "--debug-mouse");
		boolean keyDebug = hasArg(args, "--debug-key");
		boolean traceSdl = hasArg(args, "--trace-sdl");
		boolean imeSelfUi = true;
		boolean textInputCenter = false;
		boolean textInputMouse = false;
		boolean textInputZero = false;
		boolean textInputBottomLeft = false;
		boolean textInputBelow = false;
		boolean textInputNegative = true;
		boolean fullscreen = true;
		boolean startupExclusive = hasArg(args, "--startup-exclusive");
		boolean macAllowProcessSwitching =
				hasArg(args, "--mac-allow-process-switching") && !hasArg(args, "--mac-disable-process-switching");
		MacPresentationLock macPresentationLock = new MacPresentationLock();

		trace(traceSdl, "SDL_SetHint(VIDEO_MINIMIZE_ON_FOCUS_LOSS,0)");
		SDLHints.SDL_SetHint(SDLHints.SDL_HINT_VIDEO_MINIMIZE_ON_FOCUS_LOSS, "0");
		trace(traceSdl, "SDL_SetHint(VIDEO_MAC_FULLSCREEN_SPACES,0)");
		SDLHints.SDL_SetHint(SDLHints.SDL_HINT_VIDEO_MAC_FULLSCREEN_SPACES, "0");
		trace(traceSdl, "SDL_SetHint(MAC_PRESS_AND_HOLD,0)");
		SDLHints.SDL_SetHint(SDLHints.SDL_HINT_MAC_PRESS_AND_HOLD, "0");
		trace(traceSdl, "SDL_SetHint(IME_IMPLEMENTED_UI," + (imeSelfUi ? "1" : "0") + ")");
		SDLHints.SDL_SetHint(SDLHints.SDL_HINT_IME_IMPLEMENTED_UI, imeSelfUi ? "1" : "0");

		trace(traceSdl, "SDL_Init(VIDEO|EVENTS)");
		if( !SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO | SDLInit.SDL_INIT_EVENTS) ) {
			throw new IllegalStateException("SDL init failed: " + SDLError.SDL_GetError());
		}

		long windowFlags = SDLVideo.SDL_WINDOW_RESIZABLE;
		if( startupExclusive )
			windowFlags |= SDLVideo.SDL_WINDOW_FULLSCREEN;
		trace(traceSdl, "SDL_CreateWindow(flags=0x" + Long.toHexString(windowFlags) + ")");
		long window = SDLVideo.SDL_CreateWindow("SDL IME Probe", 960, 600, windowFlags);
		if( window==0L ) {
			SDLInit.SDL_Quit();
			throw new IllegalStateException("Window create failed: " + SDLError.SDL_GetError());
		}
		trace(traceSdl, "SDL_CreateRenderer");
		long renderer = SDLRender.nSDL_CreateRenderer(window, 0L);
		if( renderer==0L ) {
			SDLVideo.SDL_DestroyWindow(window);
			SDLInit.SDL_Quit();
			throw new IllegalStateException("Renderer create failed: " + SDLError.SDL_GetError());
		}
		trace(traceSdl, "SDL_SetRenderVSync(1)");
		if( !SDLRender.SDL_SetRenderVSync(renderer, 1) ) {
			System.out.println("[warn] SDL_SetRenderVSync(1) failed: " + SDLError.SDL_GetError());
		}
		final int logicalWidth = 140;
		final int logicalHeight = 192;
		final int pixelScaleX = 4;
		final int pixelScaleY = 2;
		final int patternWidth = logicalWidth * pixelScaleX;   // 560
		final int patternHeight = logicalHeight * pixelScaleY; // 384
		long patternTextureA = createPatternTexture(renderer, patternWidth, patternHeight, pixelScaleX, pixelScaleY, 0);
		long patternTextureB = createPatternTexture(renderer, patternWidth, patternHeight, pixelScaleX, pixelScaleY, 1);
		if( patternTextureA==0L || patternTextureB==0L ) {
			if( patternTextureA!=0L )
				SDLRender.nSDL_DestroyTexture(patternTextureA);
			if( patternTextureB!=0L )
				SDLRender.nSDL_DestroyTexture(patternTextureB);
			SDLRender.SDL_DestroyRenderer(renderer);
			SDLVideo.SDL_DestroyWindow(window);
			SDLInit.SDL_Quit();
			throw new IllegalStateException("Pattern texture create failed: " + SDLError.SDL_GetError());
		}

		if( fullscreen && !startupExclusive ) {
			trace(traceSdl, "SDL_SetWindowFullscreen(true)");
			SDLVideo.SDL_SetWindowFullscreen(window, true);
		}
		trace(traceSdl, "SDL_StartTextInput");
		SDLKeyboard.SDL_StartTextInput(window);
		applyConfiguredTextAnchor(window, textInputBottomLeft, textInputBelow, textInputNegative, textInputZero, textInputMouse, textInputCenter);
		trace(traceSdl, "SDL_ShowWindow");
		SDLVideo.SDL_ShowWindow(window);
		trace(traceSdl, "SDL_RaiseWindow");
		SDLVideo.SDL_RaiseWindow(window);
		if( fullscreen && !macAllowProcessSwitching && macPresentationLock.isAvailable() ) {
			macPresentationLock.setDisableProcessSwitching(true, "startup");
		}
		applyGrabState(window, fullscreen, traceSdl);
		System.out.println("SDL IME Probe started");
		System.out.println("imeSelfUi=" + imeSelfUi
				+ ", textCenter=" + textInputCenter
				+ ", textMouse=" + textInputMouse
				+ ", textZero=" + textInputZero
				+ ", textBottomLeft=" + textInputBottomLeft
				+ ", textBelow=" + textInputBelow
				+ ", textNegative=" + textInputNegative
				+ ", fullscreen=" + fullscreen
				+ ", startupExclusive=" + startupExclusive);
		System.out.println("mouseDebug=" + mouseDebug);
		System.out.println("keyDebug=" + keyDebug);
		System.out.println("macAllowProcessSwitching=" + macAllowProcessSwitching
				+ " macPresentationAvailable=" + macPresentationLock.isAvailable());
		System.out.println("Press Esc to quit.");

		java.nio.FloatBuffer mouseX = org.lwjgl.BufferUtils.createFloatBuffer(1);
		java.nio.FloatBuffer mouseY = org.lwjgl.BufferUtils.createFloatBuffer(1);
		boolean patternEnabled = true;
		boolean invertPatternPhase = false;
		boolean redraw = true;
		boolean leftMousePressed = false;
		boolean hadFocus = false;
		int currentMouseX = -1;
		int currentMouseY = -1;
		int lastMouseLogX = Integer.MIN_VALUE;
		int lastMouseLogY = Integer.MIN_VALUE;
		long lastMouseLogNs = 0L;
		final long mouseLogIntervalNs = 40_000_000L; // 40ms
		final int crosshairHalf = 5; // 11x11 crosshair span
		long perfWindowStartNs = System.nanoTime();
		long perfFrames = 0;
		long perfPresentTotalNs = 0L;
		long perfPresentMinNs = Long.MAX_VALUE;
		long perfPresentMaxNs = 0L;
		long perfDrawTotalNs = 0L;
		long perfPresentWaitTotalNs = 0L;
		java.nio.IntBuffer windowWidthBuf = org.lwjgl.BufferUtils.createIntBuffer(1);
		java.nio.IntBuffer windowHeightBuf = org.lwjgl.BufferUtils.createIntBuffer(1);
		mouseX.clear();
		mouseY.clear();
		SDLMouse.SDL_GetMouseState(mouseX, mouseY);
		currentMouseX = Math.round(mouseX.get(0));
		currentMouseY = Math.round(mouseY.get(0));

		boolean running = true;
		try( SDL_Event event = SDL_Event.malloc() ) {
			try( SDL_FRect patternDstRect = SDL_FRect.calloc() ) {
				while( running ) {
				if( textInputBottomLeft ) {
					applyBottomLeftTextInputArea(window);
				}
				else if( textInputZero ) {
					applyTextInputArea(window, 0, 0);
				}
				else if( textInputMouse ) {
					mouseX.clear();
					mouseY.clear();
					SDLMouse.SDL_GetMouseState(mouseX, mouseY);
					int x = Math.round(mouseX.get(0));
					int y = Math.round(mouseY.get(0));
					applyTextInputArea(window, x, y);
				}
				while( SDLEvents.SDL_PollEvent(event) ) {
					int type = event.type();
					if( type==SDLEvents.SDL_EVENT_QUIT || type==SDLEvents.SDL_EVENT_WINDOW_CLOSE_REQUESTED ) {
						running = false;
						break;
					}
					if( type==SDLEvents.SDL_EVENT_WINDOW_FOCUS_LOST ) {
						System.out.println("[focus] lost -> stop text input");
						SDLKeyboard.SDL_StopTextInput(window);
						if( macPresentationLock.isAvailable() )
							macPresentationLock.setDisableProcessSwitching(false, "focus_lost");
						hadFocus = false;
						leftMousePressed = false;
						continue;
					}
					if( type==SDLEvents.SDL_EVENT_WINDOW_FOCUS_GAINED ) {
						System.out.println("[focus] gained -> start text input + re-anchor");
						SDLKeyboard.SDL_StartTextInput(window);
						applyConfiguredTextAnchor(window, textInputBottomLeft, textInputBelow, textInputNegative, textInputZero, textInputMouse, textInputCenter);
						hadFocus = true;
						long flags = SDLVideo.SDL_GetWindowFlags(window);
						boolean isFullscreenNow = (flags & SDLVideo.SDL_WINDOW_FULLSCREEN) != 0L;
						applyGrabState(window, isFullscreenNow, traceSdl);
						if( macPresentationLock.isAvailable() )
							macPresentationLock.setDisableProcessSwitching(isFullscreenNow && !macAllowProcessSwitching, "focus_gained");
						continue;
					}
					if( type==SDLEvents.SDL_EVENT_KEY_DOWN || type==SDLEvents.SDL_EVENT_KEY_UP ) {
						SDL_KeyboardEvent keyEvent = event.key();
						boolean pressed = type==SDLEvents.SDL_EVENT_KEY_DOWN;
						int scancode = keyEvent.scancode();
						int key = keyEvent.key();
						short mods = keyEvent.mod();
						boolean repeat = keyEvent.repeat();
						boolean probeKey = keyDebug
								|| scancode==SDLScancode.SDL_SCANCODE_F11
								|| scancode==SDLScancode.SDL_SCANCODE_F12
								|| key==SDLKeycode.SDLK_F11
								|| key==SDLKeycode.SDLK_F12
								|| ((mods & SDLKeycode.SDL_KMOD_GUI) != 0);
						if( probeKey ) {
							System.out.println("[key] phase=" + (pressed ? "down" : "up")
									+ " key=" + key
									+ " scancode=" + scancode
									+ " repeat=" + repeat
									+ " mods=0x" + Integer.toHexString(mods & 0xffff));
						}
						if( pressed && (scancode==SDLScancode.SDL_SCANCODE_F12 || key==SDLKeycode.SDLK_F12) ) {
							long flags = SDLVideo.SDL_GetWindowFlags(window);
							boolean isFullscreen = (flags & SDLVideo.SDL_WINDOW_FULLSCREEN) != 0L;
							boolean nextFullscreen = !isFullscreen;
							System.out.println("[probe] toggle_fullscreen via F12 next=" + nextFullscreen);
							SDLVideo.SDL_SetWindowFullscreen(window, nextFullscreen);
							applyGrabState(window, nextFullscreen, traceSdl);
							if( macPresentationLock.isAvailable() )
								macPresentationLock.setDisableProcessSwitching(nextFullscreen && !macAllowProcessSwitching, "f12_toggle");
						}
						if( pressed && (scancode==SDLScancode.SDL_SCANCODE_ESCAPE || key==SDLKeycode.SDLK_ESCAPE) )
							running = false;
					}
					else if( type==SDLEvents.SDL_EVENT_TEXT_INPUT ) {
						String text = event.text().textString();
						System.out.println("[text] " + text);
					}
					else if( type==SDLEvents.SDL_EVENT_TEXT_EDITING ) {
						String text = event.edit().textString();
						System.out.println("[edit] " + text);
					}
					else if( type==SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN ) {
						int clickX = Math.round(event.button().x());
						int clickY = Math.round(event.button().y());
						currentMouseX = clickX;
						currentMouseY = clickY;
						byte button = event.button().button();
						if( mouseDebug )
							System.out.println("[click-down] x=" + clickX + " y=" + clickY + " button=" + button);
						if( button==1 )
							leftMousePressed = true;
						if( textInputMouse ) {
							applyTextInputArea(window, clickX, clickY);
							System.out.println("[anchor] x=" + clickX + " y=" + clickY + " source=click");
						}
					}
					else if( type==SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP ) {
						int releaseX = Math.round(event.button().x());
						int releaseY = Math.round(event.button().y());
						byte button = event.button().button();
						if( mouseDebug )
							System.out.println("[click-up] x=" + releaseX + " y=" + releaseY + " button=" + button);
						if( button==1 && (leftMousePressed || hadFocus) ) {
							leftMousePressed = false;
							patternEnabled = !patternEnabled;
							redraw = true;
							System.out.println("[pattern] enabled=" + patternEnabled);
						}
					}
					else if( type==SDLEvents.SDL_EVENT_MOUSE_MOTION ) {
						int mouseXPos = Math.round(event.motion().x());
						int mouseYPos = Math.round(event.motion().y());
						currentMouseX = mouseXPos;
						currentMouseY = mouseYPos;
						redraw = true;
						long nowNs = System.nanoTime();
						boolean movedEnough = Math.abs(mouseXPos - lastMouseLogX) >= 2 || Math.abs(mouseYPos - lastMouseLogY) >= 2;
						boolean timeElapsed = (nowNs - lastMouseLogNs) >= mouseLogIntervalNs;
						if( mouseDebug && movedEnough && timeElapsed ) {
							lastMouseLogX = mouseXPos;
							lastMouseLogY = mouseYPos;
							lastMouseLogNs = nowNs;
							System.out.println("[mouse] x=" + mouseXPos + " y=" + mouseYPos);
						}
					}
				}
				if( patternEnabled ) {
					invertPatternPhase = !invertPatternPhase;
					redraw = true;
				}
				if( redraw ) {
					long renderStartNs = System.nanoTime();
					SDLRender.SDL_SetRenderDrawColor(renderer, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xff);
					SDLRender.SDL_RenderClear(renderer);
					if( patternEnabled ) {
						windowWidthBuf.clear();
						windowHeightBuf.clear();
						SDLVideo.SDL_GetWindowSizeInPixels(window, windowWidthBuf, windowHeightBuf);
						int width = windowWidthBuf.get(0);
						int height = windowHeightBuf.get(0);
						double fit = Math.min(width / (double) patternWidth, height / (double) patternHeight);
						float drawW = (float) Math.max(1, Math.round(patternWidth * fit));
						float drawH = (float) Math.max(1, Math.round(patternHeight * fit));
						float drawX = (width - drawW) / 2.0f;
						float drawY = (height - drawH) / 2.0f;
						long activePatternTexture = invertPatternPhase ? patternTextureB : patternTextureA;
						patternDstRect.x(drawX);
						patternDstRect.y(drawY);
						patternDstRect.w(drawW);
						patternDstRect.h(drawH);
						SDLRender.nSDL_RenderTexture(renderer, activePatternTexture, 0L, patternDstRect.address());
					}
					if( currentMouseX>=0 && currentMouseY>=0 ) {
						byte inverse = invertPatternPhase ? (byte) 0x00 : (byte) 0xff;
						SDLRender.SDL_SetRenderDrawColor(renderer, inverse, inverse, inverse, (byte) 0xff);
						SDLRender.SDL_RenderLine(renderer, currentMouseX - crosshairHalf, currentMouseY, currentMouseX + crosshairHalf, currentMouseY);
						SDLRender.SDL_RenderLine(renderer, currentMouseX, currentMouseY - crosshairHalf, currentMouseX, currentMouseY + crosshairHalf);
					}
					long presentStartNs = System.nanoTime();
					SDLRender.SDL_RenderPresent(renderer);
					long presentElapsedNs = System.nanoTime() - presentStartNs;
					long drawElapsedNs = presentStartNs - renderStartNs;
					perfFrames++;
					perfPresentTotalNs += presentElapsedNs;
					perfDrawTotalNs += drawElapsedNs;
					perfPresentWaitTotalNs += presentElapsedNs;
					if( presentElapsedNs < perfPresentMinNs )
						perfPresentMinNs = presentElapsedNs;
					if( presentElapsedNs > perfPresentMaxNs )
						perfPresentMaxNs = presentElapsedNs;
					redraw = false;
				}
				if( fullscreen )
					SDLMouse.SDL_HideCursor();
				long nowNs = System.nanoTime();
				long perfElapsedNs = nowNs - perfWindowStartNs;
				if( perfElapsedNs >= 2_000_000_000L ) {
					double seconds = perfElapsedNs / 1_000_000_000.0;
					double fps = perfFrames / seconds;
					double avgPresentMs = perfFrames == 0 ? 0.0 : (perfPresentTotalNs / (double) perfFrames) / 1_000_000.0;
					double minPresentMs = perfFrames == 0 ? 0.0 : perfPresentMinNs / 1_000_000.0;
					double maxPresentMs = perfFrames == 0 ? 0.0 : perfPresentMaxNs / 1_000_000.0;
					double drawPercent = perfElapsedNs == 0 ? 0.0 : (perfDrawTotalNs * 100.0 / perfElapsedNs);
					double presentWaitPercent = perfElapsedNs == 0 ? 0.0 : (perfPresentWaitTotalNs * 100.0 / perfElapsedNs);
					double drawMsPerFrame = perfFrames == 0 ? 0.0 : (perfDrawTotalNs / (double) perfFrames) / 1_000_000.0;
					double presentWaitMsPerFrame = perfFrames == 0 ? 0.0 : (perfPresentWaitTotalNs / (double) perfFrames) / 1_000_000.0;
					System.out.println(String.format("[perf] fps=%.2f present_ms(avg/min/max)=%.3f/%.3f/%.3f draw_pct=%.3f%% present_wait_pct=%.3f%% draw_ms_per_frame=%.4f present_wait_ms_per_frame=%.4f frames=%d",
							fps, avgPresentMs, minPresentMs, maxPresentMs, drawPercent, presentWaitPercent, drawMsPerFrame, presentWaitMsPerFrame, perfFrames));
					perfWindowStartNs = nowNs;
					perfFrames = 0;
					perfPresentTotalNs = 0L;
					perfPresentMinNs = Long.MAX_VALUE;
					perfPresentMaxNs = 0L;
					perfDrawTotalNs = 0L;
					perfPresentWaitTotalNs = 0L;
				}
				try {
					Thread.sleep(1L);
				}
				catch( InterruptedException e ) {
					Thread.currentThread().interrupt();
					running = false;
				}
			}
			}
		}
		finally {
			if( macPresentationLock.isAvailable() )
				macPresentationLock.setDisableProcessSwitching(false, "shutdown");
			SDLKeyboard.SDL_StopTextInput(window);
			SDLRender.nSDL_DestroyTexture(patternTextureA);
			SDLRender.nSDL_DestroyTexture(patternTextureB);
			SDLRender.SDL_DestroyRenderer(renderer);
			SDLVideo.SDL_DestroyWindow(window);
			SDLInit.SDL_Quit();
		}
	}

	private static long createPatternTexture(
			long renderer,
			int patternWidth,
			int patternHeight,
			int pixelScaleX,
			int pixelScaleY,
			int phase
	) {
		long texture = SDLRender.nSDL_CreateTexture(
				renderer,
				SDLPixels.SDL_PIXELFORMAT_ARGB8888,
				SDLRender.SDL_TEXTUREACCESS_STREAMING,
				patternWidth,
				patternHeight
		);
		if( texture==0L )
			return 0L;
		java.nio.ByteBuffer patternBytes = org.lwjgl.BufferUtils.createByteBuffer(patternWidth * patternHeight * 4);
		java.nio.IntBuffer patternInts = patternBytes.asIntBuffer();
		patternInts.clear();
		for( int y = 0; y < patternHeight; y++ ) {
			int ly = y / pixelScaleY;
			for( int x = 0; x < patternWidth; x++ ) {
				int lx = x / pixelScaleX;
				boolean bright = (((lx + ly + phase) & 1) == 0);
				int c = bright ? 0xffd0d0d0 : 0xff202020;
				patternInts.put(c);
			}
		}
		patternInts.flip();
		SDLRender.nSDL_UpdateTexture(texture, 0L, MemoryUtil.memAddress(patternBytes), patternWidth * 4);
		return texture;
	}

	private static void applyTextInputArea(long window, int x, int y) {
		try( SDL_Rect.Buffer area = SDL_Rect.calloc(1) ) {
			area.x(Math.max(0, x));
			area.y(Math.max(0, y));
			area.w(1);
			area.h(1);
			SDLKeyboard.SDL_SetTextInputArea(window, area, 0);
		}
	}

	private static void applyTextInputAreaRaw(long window, int x, int y) {
		try( SDL_Rect.Buffer area = SDL_Rect.calloc(1) ) {
			area.x(x);
			area.y(y);
			area.w(1);
			area.h(1);
			SDLKeyboard.SDL_SetTextInputArea(window, area, 0);
		}
	}

	private static void applyBottomLeftTextInputArea(long window) {
		java.nio.IntBuffer wBuf = org.lwjgl.BufferUtils.createIntBuffer(1);
		java.nio.IntBuffer hBuf = org.lwjgl.BufferUtils.createIntBuffer(1);
		SDLVideo.SDL_GetWindowSizeInPixels(window, wBuf, hBuf);
		int bottomY = Math.max(0, hBuf.get(0) - 1);
		applyTextInputArea(window, 0, bottomY);
	}

	private static void applyGrabState(long window, boolean fullscreen, boolean traceSdl) {
		if( fullscreen ) {
			trace(traceSdl, "SDL_HideCursor");
			SDLMouse.SDL_HideCursor();
		}
		else {
			trace(traceSdl, "SDL_ShowCursor");
			SDLMouse.SDL_ShowCursor();
		}
		trace(traceSdl, "SDL_SetWindowKeyboardGrab(true)");
		SDLVideo.SDL_SetWindowKeyboardGrab(window, true);
		trace(traceSdl, "SDL_SetWindowMouseGrab(" + fullscreen + ")");
		SDLVideo.SDL_SetWindowMouseGrab(window, fullscreen);
		trace(traceSdl, "SDL_SetWindowRelativeMouseMode(" + fullscreen + ")");
		SDLMouse.SDL_SetWindowRelativeMouseMode(window, fullscreen);
	}

	private static void applyBelowDisplayTextInputArea(long window) {
		java.nio.IntBuffer wBuf = org.lwjgl.BufferUtils.createIntBuffer(1);
		java.nio.IntBuffer hBuf = org.lwjgl.BufferUtils.createIntBuffer(1);
		SDLVideo.SDL_GetWindowSizeInPixels(window, wBuf, hBuf);
		int belowY = Math.max(0, hBuf.get(0) + 400);
		applyTextInputArea(window, 0, belowY);
	}

	private static void applyConfiguredTextAnchor(
			long window,
			boolean textInputBottomLeft,
			boolean textInputBelow,
			boolean textInputNegative,
			boolean textInputZero,
			boolean textInputMouse,
			boolean textInputCenter
	) {
		if( textInputBottomLeft ) {
			applyBottomLeftTextInputArea(window);
		}
		else if( textInputBelow ) {
			applyBelowDisplayTextInputArea(window);
		}
		else if( textInputNegative ) {
			applyTextInputAreaRaw(window, -1, -1);
		}
		else if( textInputZero ) {
			applyTextInputArea(window, 0, 0);
		}
		else if( textInputMouse ) {
			applyTextInputArea(window, 20, 20);
		}
		else if( textInputCenter ) {
			applyTextInputArea(window, 480, 300);
		}
	}

	private static boolean hasArg(String[] args, String flag) {
		for( String arg : args ) {
			if( flag.equals(arg) )
				return true;
		}
		return false;
	}
}
