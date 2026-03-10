package device.display;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;

/**
 * macOS-only presentation option helper.
 * Uses NSApplication#setPresentationOptions to disable process switching
 * (Cmd+Tab) while requested.
 */
final class MacPresentationController {
	private static final boolean IS_MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");
	private static final long NS_APP_PRESENTATION_AUTO_HIDE_DOCK = 1L << 0;
	private static final long NS_APP_PRESENTATION_DISABLE_PROCESS_SWITCHING = 1L << 5;

	private final boolean available;
	private final Function objcMsgSend;
	private final long nsApplicationClass;
	private final long selSharedApplication;
	private final long selSetPresentationOptions;

	private long cachedAppInstance;
	private boolean processSwitchingDisabled;

	MacPresentationController() {
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
			throw new RuntimeException("Failed to initialize macOS presentation controller", t);
		}
	}

	boolean isAvailable() {
		return available;
	}

	void setDisableProcessSwitching(boolean enabled) {
		if( !available )
			return;
		if( processSwitchingDisabled==enabled )
			return;
		long app = appInstance();
		if( app==0L )
			return;
		// macOS requires DisableProcessSwitching to be paired with Dock hide/auto-hide.
		long options = enabled
				? (NS_APP_PRESENTATION_AUTO_HIDE_DOCK | NS_APP_PRESENTATION_DISABLE_PROCESS_SWITCHING)
				: 0L;
		objcMsgSend.invokeVoid(new Object[] {app, selSetPresentationOptions, options});
		processSwitchingDisabled = enabled;
	}

	private long appInstance() {
		if( cachedAppInstance!=0L )
			return cachedAppInstance;
		cachedAppInstance = objcMsgSend.invokeLong(new Object[] {nsApplicationClass, selSharedApplication});
		return cachedAppInstance;
	}
}
