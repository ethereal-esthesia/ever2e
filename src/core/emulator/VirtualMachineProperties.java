package core.emulator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

public class VirtualMachineProperties {

	public static enum MachineLayoutType {
		DEMO_32x32,
		DEMO_32x32_CONSOLE,
		APPLE_IIE,
		DEBUG_65C02
	}

	private static final String EMU_EXTENSION = ".emu";

	private Properties properties;
	private File propertiesDirectory;
	private MachineLayoutType layout;
	private String[] slotLayout = new String[7];
	private int programStart;
	private byte[] program;
	
	public VirtualMachineProperties( String propertiesFileName ) throws IOException {
		if( !propertiesFileName.substring(propertiesFileName.length()-4).equalsIgnoreCase(EMU_EXTENSION) )
			propertiesFileName += EMU_EXTENSION;
		properties = new Properties();
		File propertiesFile = new File(propertiesFileName);
		propertiesDirectory = propertiesFile.getParentFile();
		properties.load(new FileInputStream(propertiesFile));
		this.layout = MachineLayoutType.valueOf(properties.getProperty("machine.layout"));
		this.programStart = Integer.decode(properties.getProperty("address.start"));
		FileInputStream binStream;
		String fileName = properties.getProperty("binary.file");
		try {
			binStream = new FileInputStream(propertiesFile.getParent()+"/"+fileName);
		} catch ( FileNotFoundException e ) {
			binStream = new FileInputStream(fileName);
		}
		try {
			program = new byte[0x10000];
			int len = binStream.read(program, 0, 0x10000);
			program = Arrays.copyOf(program, len);
		} finally {
			binStream.close();
		}
		for( Integer i = 1; i<=7; i++ ) {
			String layoutStr = properties.getProperty("machine.layout.slot."+i, null);
			if( layoutStr!=null && layoutStr.length()>0 )
				slotLayout[i-1] = layoutStr;
		}
	}
	public MachineLayoutType getLayout() {
		return layout;
	}
	public int getProgramStart() {
		return programStart;
	}
	public byte[] getCode() {
		return program;
	}
	public String getSlotLayout( int slot ) {
		return slotLayout[slot-1];
	}
	public void setProperty( String propertyStr, String value ) {
		if( value==null )
			properties.remove(propertyStr);
		else
			properties.setProperty(propertyStr, value);
		for( int i = 1; i<=7; i++ )
			if( propertyStr.equals("machine.layout.slot."+i) ) {
				slotLayout[i-1] = value==null || value.length()==0 ? null : value;
				break;
			}
	}
	public String getProperty( String propertyStr ) {
		return properties.getProperty(propertyStr);
	}
	public String getProperty( String propertyStr, String defaultStr ) {
		return properties.getProperty(propertyStr, defaultStr);
	}
	public File resolvePath( String fileName ) {
		File file = new File(fileName);
		if( file.isAbsolute() || propertiesDirectory==null )
			return file;
		File relativeFile = new File(propertiesDirectory, fileName);
		return relativeFile.exists() ? relativeFile : file;
	}
	
	@Override
	public String toString() {
		return "Machine Properties [layout=" + layout + ", programStart="
				+ "0x"+Integer.toHexString(programStart) + ", size=" + "0x"+Integer.toHexString(program.length) + "]";
	}

}
