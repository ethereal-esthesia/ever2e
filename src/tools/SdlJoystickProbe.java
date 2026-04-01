package tools;

import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLJoystick;
import org.lwjgl.sdl.SDL_Event;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Enumerates SDL joystick/gamepad devices and prints live axis/button activity.
 */
public final class SdlJoystickProbe {
	private SdlJoystickProbe() {
	}

	private static final class DeviceState {
		long gamepad;
		long joystick;
		final short[] axis = new short[8];
		final boolean[] button = new boolean[16];
	}

	public static void main(String[] args) {
		if( !SDLInit.SDL_Init(SDLInit.SDL_INIT_EVENTS | SDLInit.SDL_INIT_GAMEPAD | SDLInit.SDL_INIT_JOYSTICK) ) {
			throw new IllegalStateException("SDL init failed: " + SDLError.SDL_GetError());
		}
		SDLGamepad.SDL_SetGamepadEventsEnabled(true);
		SDLJoystick.SDL_SetJoystickEventsEnabled(true);

		Map<Integer, DeviceState> devices = new HashMap<>();
		openAllDevices(devices);
		printDeviceInventory(devices);

		System.out.println("Joystick probe running. Move sticks / press buttons (Ctrl+C to stop).");
		long nextSnapshotNs = System.nanoTime() + 1_000_000_000L;

		try( SDL_Event event = SDL_Event.malloc() ) {
			while( true ) {
				while( SDLEvents.SDL_PollEvent(event) ) {
					int type = event.type();
					if( type==SDLEvents.SDL_EVENT_QUIT )
						return;
					if( type==SDLEvents.SDL_EVENT_GAMEPAD_ADDED || type==SDLEvents.SDL_EVENT_GAMEPAD_REMOVED ||
							type==SDLEvents.SDL_EVENT_JOYSTICK_ADDED || type==SDLEvents.SDL_EVENT_JOYSTICK_REMOVED ) {
						closeAll(devices);
						openAllDevices(devices);
						printDeviceInventory(devices);
						continue;
					}
					if( type==SDLEvents.SDL_EVENT_GAMEPAD_AXIS_MOTION ) {
						int id = event.gaxis().which();
						int axis = event.gaxis().axis() & 0xff;
						short value = event.gaxis().value();
						DeviceState s = devices.get(id);
						if( s!=null && axis<s.axis.length )
							s.axis[axis] = value;
						System.out.println("[gaxis] id=" + id + " axis=" + axis + " value=" + value);
						continue;
					}
					if( type==SDLEvents.SDL_EVENT_GAMEPAD_BUTTON_DOWN || type==SDLEvents.SDL_EVENT_GAMEPAD_BUTTON_UP ) {
						int id = event.gbutton().which();
						int button = event.gbutton().button() & 0xff;
						boolean down = event.gbutton().down();
						DeviceState s = devices.get(id);
						if( s!=null && button<s.button.length )
							s.button[button] = down;
						System.out.println("[gbtn ] id=" + id + " button=" + button + " down=" + down);
						continue;
					}
					if( type==SDLEvents.SDL_EVENT_JOYSTICK_AXIS_MOTION ) {
						int id = event.jaxis().which();
						int axis = event.jaxis().axis() & 0xff;
						short value = event.jaxis().value();
						DeviceState s = devices.get(id);
						if( s!=null && axis<s.axis.length )
							s.axis[axis] = value;
						System.out.println("[jaxis] id=" + id + " axis=" + axis + " value=" + value);
						continue;
					}
					if( type==SDLEvents.SDL_EVENT_JOYSTICK_BUTTON_DOWN || type==SDLEvents.SDL_EVENT_JOYSTICK_BUTTON_UP ) {
						int id = event.jbutton().which();
						int button = event.jbutton().button() & 0xff;
						boolean down = event.jbutton().down();
						DeviceState s = devices.get(id);
						if( s!=null && button<s.button.length )
							s.button[button] = down;
						System.out.println("[jbtn ] id=" + id + " button=" + button + " down=" + down);
					}
				}

				long now = System.nanoTime();
				if( now>=nextSnapshotNs ) {
					printSnapshot(devices);
					nextSnapshotNs = now + 1_000_000_000L;
				}

				Thread.sleep(5L);
			}
		}
		catch( InterruptedException e ) {
			Thread.currentThread().interrupt();
		}
		finally {
			closeAll(devices);
			SDLInit.SDL_Quit();
		}
	}

	private static void openAllDevices(Map<Integer, DeviceState> out) {
		out.clear();
		IntBuffer gamepads = SDLGamepad.SDL_GetGamepads();
		if( gamepads!=null ) {
			while( gamepads.hasRemaining() ) {
				int id = gamepads.get();
				DeviceState state = out.computeIfAbsent(id, ignored -> new DeviceState());
				state.gamepad = SDLGamepad.SDL_OpenGamepad(id);
				if( state.joystick==0L && state.gamepad!=0L )
					state.joystick = SDLGamepad.SDL_GetGamepadJoystick(state.gamepad);
			}
		}
		IntBuffer joysticks = SDLJoystick.SDL_GetJoysticks();
		if( joysticks!=null ) {
			while( joysticks.hasRemaining() ) {
				int id = joysticks.get();
				DeviceState state = out.computeIfAbsent(id, ignored -> new DeviceState());
				if( state.joystick==0L )
					state.joystick = SDLJoystick.SDL_OpenJoystick(id);
			}
		}
	}

	private static void printDeviceInventory(Map<Integer, DeviceState> devices) {
		System.out.println("Detected devices:");
		if( devices.isEmpty() ) {
			System.out.println("  (none)");
			return;
		}
		for( Map.Entry<Integer, DeviceState> entry : devices.entrySet() ) {
			int id = entry.getKey();
			DeviceState s = entry.getValue();
			String gname = s.gamepad!=0L ? SDLGamepad.SDL_GetGamepadName(s.gamepad) : "";
			String jname = s.joystick!=0L ? SDLJoystick.SDL_GetJoystickName(s.joystick) : "";
			int axisCount = s.joystick!=0L ? SDLJoystick.SDL_GetNumJoystickAxes(s.joystick) : 0;
			int buttonCount = s.joystick!=0L ? SDLJoystick.SDL_GetNumJoystickButtons(s.joystick) : 0;
			System.out.println("  id=" + id +
					" gamepad=" + (s.gamepad!=0L) +
					(gname==null || gname.isEmpty() ? "" : (" name=\"" + gname + "\"")) +
					(jname==null || jname.isEmpty() ? "" : (" joystick=\"" + jname + "\"")) +
					" axes=" + axisCount +
					" buttons=" + buttonCount);
		}
	}

	private static void printSnapshot(Map<Integer, DeviceState> devices) {
		if( devices.isEmpty() )
			return;
		for( Map.Entry<Integer, DeviceState> entry : devices.entrySet() ) {
			int id = entry.getKey();
			DeviceState s = entry.getValue();
			System.out.println("[snap ] id=" + id +
					" ax0=" + s.axis[0] +
					" ax1=" + s.axis[1] +
					" b0=" + (s.button[0] ? 1 : 0) +
					" b1=" + (s.button[1] ? 1 : 0) +
					" b2=" + (s.button[2] ? 1 : 0) +
					" b7=" + (s.button[7] ? 1 : 0));
		}
	}

	private static void closeAll(Map<Integer, DeviceState> devices) {
		for( DeviceState s : devices.values() ) {
			if( s.gamepad!=0L )
				SDLGamepad.SDL_CloseGamepad(s.gamepad);
			if( s.joystick!=0L && s.gamepad==0L)
				SDLJoystick.SDL_CloseJoystick(s.joystick);
			s.gamepad = 0L;
			s.joystick = 0L;
		}
	}
}
