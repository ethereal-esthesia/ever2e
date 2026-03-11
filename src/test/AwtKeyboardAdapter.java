package test;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import device.keyboard.KeyboardIIe;

/**
 * Legacy AWT test harness adapter that forwards host key events into KeyboardIIe.
 */
final class AwtKeyboardAdapter extends KeyAdapter {
	private final KeyboardIIe keyboard;

	AwtKeyboardAdapter(KeyboardIIe keyboard) {
		this.keyboard = keyboard;
	}

	@Override
	public void keyPressed(KeyEvent event) {
		keyboard.keyPressedRaw(
				event.getKeyCode(),
				event.getModifiersEx(),
				event.getKeyChar(),
				event.isShiftDown(),
				event.isControlDown(),
				event.isAltDown(),
				event.isMetaDown());
	}

	@Override
	public void keyReleased(KeyEvent event) {
		keyboard.keyReleasedRaw(
				event.getKeyCode(),
				event.getModifiersEx(),
				event.getKeyChar(),
				event.isShiftDown(),
				event.isControlDown(),
				event.isAltDown(),
				event.isMetaDown());
	}
}
