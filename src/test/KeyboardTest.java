package test;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.DefaultEditorKit;

import core.exception.HardwareException;
import device.keyboard.KeyboardIIe;

public class KeyboardTest extends JFrame {

	private static final long serialVersionUID = -7209529104796665295L;

	private static final String NEWLINE = System.getProperty("line.separator");

	private static KeyboardIIe keyboard;
	public JTextArea displayArea;

	static int keyCode = 0;
	
	public static void main(String[] args) throws InterruptedException, HardwareException {

		KeyboardTest frame = new KeyboardTest();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		frame.pack();
		frame.setVisible(true);

		while( true ) {
			if( keyCode!=keyboard.getHeldKeyCode() ) {
				frame.displayArea.append("Change: "+keyboard.getHeldKeyCode()+
						" ("+Character.toChars(keyboard.getHeldKeyCode()&0x7f)[0]+")"+NEWLINE);
				keyCode = keyboard.getHeldKeyCode();
			}
			Thread.sleep(10);
		}
		
	}
	
	KeyboardTest() throws HardwareException {

		displayArea = new JTextArea();
		keyboard = new KeyboardIIe(0, null);

		displayArea.setEditable(false);
		displayArea.addKeyListener(new AwtKeyboardAdapter(keyboard));
		displayArea.setFocusTraversalKeysEnabled(false);
		displayArea.getActionMap().get(DefaultEditorKit.deleteNextCharAction).setEnabled(false);
		displayArea.getActionMap().get(DefaultEditorKit.deletePrevCharAction).setEnabled(false);
		displayArea.getActionMap().get(DefaultEditorKit.insertTabAction).setEnabled(false);

		//JButton button = new JButton("Clear");
		//button.addActionListener(keyListener);

		JScrollPane scrollPane = new JScrollPane(displayArea);
		scrollPane.setPreferredSize(new Dimension(640, 480));

		getContentPane().add(scrollPane, BorderLayout.CENTER);
		//getContentPane().add(button, BorderLayout.PAGE_END);

	}

}
