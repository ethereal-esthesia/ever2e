package device.keyboard;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import core.emulator.HardwareManager;

public abstract class Keyboard extends HardwareManager {

	protected Set<Integer> keyPressed = new HashSet<>();

	public Keyboard( long unitsPerCycle ) {
		super(unitsPerCycle);
	}
	
	public void keyPressed(int keyCode) {
		keyPressed.add(keyCode);
	}

	public void keyReleased(int keyCode) {
		keyPressed.remove(keyCode);
	}
	
	public boolean isKeyPressed( int keyIndex ) {
		return keyPressed.contains(keyIndex);
	}
	
	public abstract int getHeldKeyCode();
	
	public abstract int getTypedKeyCode();
	
	public String toString() {
		StringBuilder str = new StringBuilder();
		Iterator<Integer> i = keyPressed.iterator();
		while( i.hasNext() ) 
			str.append(i.next());
		return str.toString();
	}

}
