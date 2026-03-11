package device.display;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

import core.exception.HardwareException;
import core.memory.memory8.MemoryBus8;
import device.keyboard.KeyboardIIe;

public class Display32x32 extends DisplayWindow {

	private static final int PIXEL_MULT = 16;
	
	private static final int XSIZE = 32*PIXEL_MULT;
	private static final int YSIZE = 32*PIXEL_MULT;
	
	private static final int [] pal = new int[] {
		new Color(0, 0, 0).getRGB(),
		new Color(255, 255, 255).getRGB(),
		new Color(133, 0, 5).getRGB(),
		new Color(175, 255, 238).getRGB(),
		new Color(202, 54, 204).getRGB(),
		new Color(38, 207, 85).getRGB(),
		new Color(20, 0, 169).getRGB(),
		new Color(237, 241, 122).getRGB(),
		new Color(218, 137, 88).getRGB(),
		new Color(100, 69, 3).getRGB(),
		new Color(251, 118, 121).getRGB(),
		new Color(51, 51, 51).getRGB(),
		new Color(119, 119, 119).getRGB(),
		new Color(172, 255, 104).getRGB(),
		new Color(45, 129, 254).getRGB(),
		new Color(187, 187, 187).getRGB()
	};
	
	private class Canvas32x32 extends Canvas {
		
		private static final long serialVersionUID = 3277512952021171260L;
		
		BufferedImage rawDisplay;
		
		public Canvas32x32() {
			super();
			rawDisplay = new BufferedImage(XSIZE, YSIZE, BufferedImage.TYPE_INT_RGB);
			setSize(XSIZE, YSIZE);
		}
	
		public void paint( Graphics g ){
			for( int y = 0; y < YSIZE; y++ )
				for( int x = 0; x < XSIZE; x++ )
					rawDisplay.setRGB(x, y, pal[0x0f&memoryBus.getByte(getAddressLo(y/PIXEL_MULT, x/PIXEL_MULT))]);
			g.drawImage(rawDisplay, 0, 0, this);
		}
	
	}
	
	private Frame frame;
	private Canvas32x32 canvas;
	private MemoryBus8 memoryBus;
	
	public Display32x32(MemoryBus8 memoryBus, KeyboardIIe keyboard, long unitsPerCycle) {
		super(unitsPerCycle);
		this.memoryBus = memoryBus;
		canvas = new Canvas32x32();
		frame = new Frame("32x32x16@0200h 65C02 Emulator");
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent windowEvent){
				System.exit(0);
			}        
		});    
		frame.add(canvas);
		frame.setVisible(true);  
		frame.setSize(XSIZE, YSIZE+frame.getInsets().top);
		frame.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent event) {
				keyboard.keyPressedRaw(event.getKeyCode(), event.getModifiers(), event.getKeyChar(),
						event.isShiftDown(), event.isControlDown(), event.isAltDown(), event.isMetaDown());
			}

			@Override
			public void keyReleased(KeyEvent event) {
				keyboard.keyReleasedRaw(event.getKeyCode(), event.getModifiers(), event.getKeyChar(),
						event.isShiftDown(), event.isControlDown(), event.isAltDown(), event.isMetaDown());
			}
		});
		canvas.repaint();
	}

	@Override
	public void cycle() throws HardwareException {
		incSleepCycles(1);
		canvas.repaint();
	}

	public static int getAddressLo( int scanline, int offset )
	{
		return 0x0200+(scanline<<5)+offset;
	}

	@Override
	public void coldReset() throws HardwareException {
	}

}
