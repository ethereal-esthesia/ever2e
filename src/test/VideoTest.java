package test;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

import core.exception.HardwareException;
import device.keyboard.KeyboardIIe;

public class VideoTest {

	private Frame frame;
	private Canvas canvas;
	private MyBuffer image;
	private static KeyboardIIe keyboard;

	static int keyCode = 0;
	
	public VideoTest() throws InterruptedException, HardwareException{
		keyboard = new KeyboardIIe(0, null);
		prepareGUI();
	}

	public static void main(String[] args) throws InterruptedException, HardwareException{
		VideoTest  awtControlDemo = new VideoTest();
		awtControlDemo.showCanvasDemo();
	}

	private void prepareGUI() throws InterruptedException{
		frame = new Frame("Java AWT Test");
		frame.setSize(640,480);
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent windowEvent){
				System.exit(0);
			}        
		});    
		canvas = new MyCanvas();
		image = new MyBuffer();
		frame.add(canvas);
		AwtKeyboardAdapter keyAdapter = new AwtKeyboardAdapter(keyboard);
		frame.addKeyListener(keyAdapter);
		canvas.addKeyListener(keyAdapter);
		frame.setVisible(true);  
		canvas.repaint();
		while( true ) {
			Thread.sleep(16);
			if( (keyCode&0x80)!=(keyboard.getHeldKeyCode()&0x80) ) {
				canvas.repaint();
				keyCode = keyboard.getHeldKeyCode();
			}
		}
	}

	private void showCanvasDemo(){
	} 

	class MyCanvas extends Canvas {

		private static final long serialVersionUID = 5169102726529321308L;

		public MyCanvas () {
			setBackground (Color.BLACK);
			setSize(640, 480);
		}

		boolean a = false;

		public void paint (Graphics g) {
			a = !a;
			if(a)
				image.draw1();
			else
				image.draw2();
			g.drawImage(image, 0, 0, this);

			//	     Graphics2D g2;
			//	     g2 = (Graphics2D) g;
			//	     g2.drawString ("It is a custom canvas area", 70, 70);
			//	     g2.
		}
	}

	class MyBuffer extends BufferedImage {

		public MyBuffer () {
			super(640, 480, BufferedImage.TYPE_INT_RGB);
		}

		public void draw1(){
			int color = Color.BLACK.getRGB();
			for( int x = 0; x < getWidth(); x++) {
				for (int y = 0; y < getHeight(); y++) {
					setRGB(x, y, color);
				}
			}
			color = Color.WHITE.getRGB();
			// Implement rectangle drawing
			for (int x = 0; x < 640/2; x++) {
				for (int y = 0; y< 480/2; y++) {
					setRGB(x, y, color);
				}
			}
		}

		public void draw2(){
			int color = Color.WHITE.getRGB();
			for( int x = 0; x < getWidth(); x++) {
				for (int y = 0; y < getHeight(); y++) {
					setRGB(x, y, color);
				}
			}
			color = Color.BLACK.getRGB();
			// Implement rectangle drawing
			for (int x = 0; x < 640/2; x++) {
				for (int y = 0; y< 480/2; y++) {
					setRGB(x, y, color);
				}
			}
		}

	}
}
