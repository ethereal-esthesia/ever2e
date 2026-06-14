package peripherals.drive.floppy525;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import peripherals.PeripheralIIe;
import core.emulator.VirtualMachineProperties;
import core.exception.HardwareException;
import core.memory.memory8.MemoryBusIIe.SwitchSet8;

/*
 * The following pages in Sather's Understanding the Apple IIe
 * were used as a reference
 * 9-7 stepper motor
 * 9-12 overview
 * 9-14 data register
 * 9-15 sequencer
 * 9-21 write protect
 * 9-25 write example
 * 9-26 Group Code Recording translation tables & read example
 * 9-27 detailed read explanation
 * 9-27 / 9-30 syncing strategy
 * 9-28 disk byte layout
 */

public class Floppy525Controller extends PeripheralIIe {

	private String[] fileName = new String[2];

	private static final byte[] DOS_ORDER_SECTORS = { 0, 7, 14, 6, 13, 5, 12, 4, 11, 3, 10, 2, 9, 1, 8, 15 };
	
	private static final byte[] PRODOS_ORDER_SECTORS = { 0, 8, 1, 9, 2, 10, 3, 11, 4, 12, 5, 13, 6, 14, 7, 15 };
	
	private static final byte[] ROM = parseHexBinary(
				"A220A000A203863C8A0A243CF010053C49FF297EB0084AD0FB989D5603C8E810"+
				"E52058FFBABD00010A0A0A0A852BAABD8EC0BD8CC0BD8AC0BD89C0A050BD80C0"+
				"9829030A052BAABD81C0A95620A8FC8810EB8526853D8541A90885271808BD8C"+
			"C010FB49D5D0F7BD8CC010FBC9AAD0F3EABD8CC010FBC996F0092890DF49ADF0"+
			"25D0D9A0038540BD8CC010FB2A853CBD8CC010FB253C88D0EC28C53DD0BEA540"+
			"C541D0B8B0B7A056843CBC8CC010FB59D602A43C88990003D0EE843CBC8CC010"+
				"FB59D602A43C9126C8D0EFBC8CC010FB59D602D087A000A256CA30FBB1265E00"+
				"032A5E00032A9126C8D0EEE627E63DA53DCD0008A62B90DB4C01080000000000");

	private static byte[] parseHexBinary(String hex) {
		int len = hex.length();
		if( (len & 1) != 0 )
			throw new IllegalArgumentException("Hex string must have an even length");
		byte[] out = new byte[len / 2];
		for( int i = 0; i < len; i += 2 ) {
			int hi = Character.digit(hex.charAt(i), 16);
			int lo = Character.digit(hex.charAt(i + 1), 16);
			if( hi < 0 || lo < 0 )
				throw new IllegalArgumentException("Invalid hex digit in ROM data at index " + i);
			out[i / 2] = (byte) ((hi << 4) | lo);
		}
		return out;
	}

	private static final String EXT_PRODOS_ORDER = "PO";
	private static final String EXT_DOS_ORDER = "DSK";
	private static final String EXT_DOS_ORDER_EXPLICIT = "DO";
	private static final String NIBBLE_EXT = "NIB";

	private static final int TRACK_TOTAL = 35;
	private static final int SECTOR_TOTAL = 16;
	private static final int SECTOR_BYTES = 416;
	private static final int TRACK_BYTES = SECTOR_BYTES*SECTOR_TOTAL;

	private static final int PHASE0_MASK = 0x01;
	private static final int PHASE1_MASK = 0x02;
	private static final int PHASE2_MASK = 0x04;
	private static final int PHASE3_MASK = 0x08;
	
	private static final int PHASE_SHIFT[] = new int[] {
		-1, // 0000
		 0, // 0001
		 1, // 0010
		-1, // 0011
		 2, // 0100
		-1, // 0101
		-1, // 0110
		-1, // 0111
		 3, // 1000
		-1, // 1001
		-1, // 1010
		-1, // 1011
		-1, // 1100
		-1, // 1101
		-1, // 1110
		-1  // 1111
	};
	
	private int slot;

	private int dataRegister = 0;
	private int writeRequestRegister = 0;
	private int writeRegister = -1;

	private boolean driveWrite;

	private boolean driveOn;
	private boolean driveOnPrevious;
	private int driveSelect;
	private int driveOffRequest;
	private boolean writeOn;

	private int[] phase = new int[2];	
	private int[] headHalfTrack = new int[2];
	private int[] headSectorByte = new int[2];

	private boolean[] readOnly = new boolean[2];
	private byte[][] diskImage;

//	private int[] dataShift = new int[2];
//	private int[] dataSelect = new int[2];
	private SwitchSet8 switchSet = new SwitchSet8() {

		@Override
		public int readMem( int address ){

			Boolean msb = null;
			
			// Drive switch information found in Sather 9-12
			switch( address&0x000f ) {
			case 0x00:  // Phase 0 off
				if( driveOn ) {
					phase[driveSelect] &= ~PHASE0_MASK;
					moveHead();
				}
				break;
			case 0x01:  // Phase 0 on
				if( driveOn ) {
					phase[driveSelect] |= PHASE0_MASK;
					moveHead();
				}
				break;
			case 0x02:  // Phase 1 off
				if( driveOn ) {
					phase[driveSelect] &= ~PHASE1_MASK;
					moveHead();
				}
				break;
			case 0x03:  // Phase 1 on
				if( driveOn ) {
					phase[driveSelect] |= PHASE1_MASK;
					moveHead();
				}
				break;
			case 0x04:  // Phase 2 off
				if( driveOn ) {
					phase[driveSelect] &= ~PHASE2_MASK;
					moveHead();
				}
				break;
			case 0x05:  // Phase 2 on
				if( driveOn ) {
					phase[driveSelect] |= PHASE2_MASK;
					moveHead();
				}
				break;
			case 0x06:  // Phase 3 off
				if( driveOn ) {
					phase[driveSelect] &= ~PHASE3_MASK;
					moveHead();
				}
				break;
			case 0x07:  // Phase 3 on
				if( driveOn ) {
					phase[driveSelect] |= PHASE3_MASK;
					moveHead();
				}
				break;
			case 0x08:  // Drive off
				if( driveOn && driveOffRequest==-1 )
					driveOffRequest = 0;
				break;
			case 0x09:  // Drive on
				startDrive();
				driveOffRequest = -1;
				break;
			case 0x0a:  // Drive 1 select
				setDrive(1);
				break;
			case 0x0b:  // Drive 2 select
				setDrive(2);
				break;
			case 0x0c:  // Poll read / set up reading
				if( writeOn )
					writeRegister = writeRequestRegister;
				break;
			case 0x0d:  // Load (needed for writes)
				break;
			case 0x0e:  // Read
				writeOn = false;
				if( driveOn )
					msb = readOnly[driveSelect];
				break;
			case 0x0f:  // Write
				writeOn = true;
				break;
			}
			
			int data = msb==null ? dataRegister:( msb ? dataRegister|0x80:dataRegister&0x7f );
			if( (dataRegister&0x80)!=0 )
				dataRegister = 0x00;
			return data;
		}
		
		private void moveHead() {
			int phaseShift = PHASE_SHIFT[phase[driveSelect]];
			int currentPhase = headHalfTrack[driveSelect]&0x03;
			if( phaseShift>=0 ) {
				// TODO add required delay
				int currentTrack = headHalfTrack[driveSelect];
				if( Math.abs(phaseShift-currentPhase)==1 )
					headHalfTrack[driveSelect] += phaseShift-currentPhase;
				else if( Math.abs(phaseShift-currentPhase)==3 )
					headHalfTrack[driveSelect] -= (phaseShift-currentPhase)>0 ? 1:-1;
				if( headHalfTrack[driveSelect]<0 )
					headHalfTrack[driveSelect] = 0;
				else if( headHalfTrack[driveSelect]>=TRACK_TOTAL<<1 )
					headHalfTrack[driveSelect] = (TRACK_TOTAL<<1)-1;
				if( currentTrack!=headHalfTrack[driveSelect] )
					System.out.println("Slot "+slot+", drive "+getDrive()+", track "+headHalfTrack[driveSelect]/2d+" selected");
			}
		}

		@Override
		public void writeMem( int address, int value ){
			if( ((address&0x000f)==0x000d || (address&0x000f)==0x000f) && driveOn ) 
				writeRequestRegister = value;
			driveWrite = true;
			readMem(address);
		}

		@Override
		public void warmReset() {
			setDrive(1);
			killDrive();
			writeOn = false;
			writeOn = false;
		}

	};

	private void displayDriveStatus() {
		if( driveOnPrevious!=driveOn ) {
			System.out.println("Slot "+slot+", drive "+getDrive()+" "+
					(driveOn ? "started":"stopped"));
			driveOnPrevious = driveOn;
		}
	}

	public Floppy525Controller( int slot, long unitsPerCycle, VirtualMachineProperties properties ) throws IOException, HardwareException {
		super(unitsPerCycle);
		this.slot = slot;
		fileName[0] = properties.getProperty("machine.layout.slot."+slot+".drive.1.file", null);
		fileName[1] = properties.getProperty("machine.layout.slot."+slot+".drive.2.file", null);
		driveOnPrevious = false;
		headHalfTrack[0] = 69;
		headHalfTrack[1] = 69;
		driveOffRequest = -1;
		coldReset();
	}

	@Override
	public void coldReset() throws HardwareException {
		switchSet.warmReset();
	}

	@Override
	public void cycle() throws HardwareException {
		
/*		TODO: the following shift-method may be used in future implementations,
 *            but not with existing emulated disk formats
 *            due to lack of nibble cycle-timing calculations when generating and
 *            even reading in images on existing emulators

		incSleepCycles(4);

		if( driveOn ){
			dataSelect[driveSelect] <<= 1;
			dataSelect[driveSelect] &= 0xffff;
			dataShift[driveSelect]++;
			if( dataShift[driveSelect]==8 ) {
				dataShift[driveSelect] = 0;
				headSectorByte[driveSelect]++;
				if( headSectorByte[driveSelect]==TRACK_BYTES )
					headSectorByte[driveSelect] = 0;
				dataSelect[driveSelect] |= Byte.toUnsignedInt(
						diskImage[headHalfTrack[driveSelect]>>1][headSectorByte[driveSelect]]);
			}
			if( (dataSelect[driveSelect]&0x8000)!=0 ) {
				dataRegister = dataSelect[driveSelect]>>8;
				dataSelect[driveSelect] &= 0xff;
			} else if( (dataRegister&0x80)==0 )
				dataRegister = dataSelect[driveSelect]>>8;
		}
*/		

		if( driveOn ) {
			headSectorByte[driveSelect]++;
			if( headSectorByte[driveSelect]==TRACK_BYTES )
				headSectorByte[driveSelect] = 0;
			if( !writeOn ) {
				dataRegister = Byte.toUnsignedInt(
						diskImage[headHalfTrack[driveSelect]>>1][headSectorByte[driveSelect]]);
			}
		}

		incSleepCycles(dataRegister==0xff||writeRegister==0xff ? 32/*36*/:32);

		if( writeRegister>=0 ) {
			diskImage[headHalfTrack[driveSelect]>>1][headSectorByte[driveSelect]] =
					(byte) writeRegister;
			writeRegister = -1;
		}
		
		for( int drive = 0; drive<2; drive++ ) {
			if( driveOffRequest>=0 &&
					driveOffRequest++==0x40000>>3 ) {
				driveOffRequest = -1;
				killDrive();
			}
		}

	}
	
	private void loadImage( int drive ) {
		
		diskImage = new byte[TRACK_TOTAL][TRACK_BYTES];
		FileInputStream binStream = null;
		try {
			String fileName = this.fileName[drive].trim();
			File file = new File(fileName);
			readOnly[drive] = !file.canWrite();
			binStream = new FileInputStream(file);
			String fileExt = fileName.trim().substring(fileName.lastIndexOf('.')+1).toUpperCase();
			switch( fileExt ) {
			case EXT_PRODOS_ORDER:
				for( int track = 0; track<TRACK_TOTAL; track++ )
					for( int sector = 0; sector<16; sector++ )
						binStream.read(diskImage[track], sector*SECTOR_BYTES+0, 0x100);
				throw new IOException("ProDOS images not yet supported, please convert to NIB format.");
			case EXT_DOS_ORDER:
			case EXT_DOS_ORDER_EXPLICIT:
				for( int track = 0; track<TRACK_TOTAL; track++ )
					for( int sector = 0; sector<16; sector++ )
						binStream.read(diskImage[track], sector*SECTOR_BYTES+0, 0x100);
				throw new IOException("DOS images not yet supported, please convert to NIB format.");
			case NIBBLE_EXT:
				for( int track = 0; track<TRACK_TOTAL; track++ )
					binStream.read(diskImage[track], 0, TRACK_BYTES);
				break;
			default:
				throw new IOException(fileExt+" format not supported.");
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if( binStream!=null ) {
				try {
					binStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
	}

	private void saveImage( int drive ) {
		if( readOnly[drive] )
			return;

		FileOutputStream binStream = null;
		try {
			String fileName = this.fileName[drive].trim();
			binStream = new FileOutputStream(fileName);
			String fileExt = fileName.trim().substring(fileName.lastIndexOf('.')+1).toUpperCase();
			switch( fileExt ) {
			case EXT_PRODOS_ORDER:
				throw new IOException("ProDOS images not yet supported, please convert to NIB format.");
			case EXT_DOS_ORDER:
			case EXT_DOS_ORDER_EXPLICIT:
				throw new IOException("DOS images not yet supported, please convert to NIB format.");
			case NIBBLE_EXT:
				for( int track = 0; track<TRACK_TOTAL; track++ )
					binStream.write(diskImage[track], 0, TRACK_BYTES);
				break;
			default:
				throw new IOException(fileExt+" format not supported.");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if( binStream!=null )
					binStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	private void setDrive( int drive ) {
		if( drive-1==driveSelect )
			return;
		if( driveOn ) {
			killDrive();
			driveSelect = drive-1;
			startDrive();
		} else
			driveSelect = drive-1;
	}

	public int getDrive() {
		return driveSelect+1;
	}

	private void startDrive() {
		if( driveOn )
			return;
		driveOn = true;
		displayDriveStatus();
		loadImage(driveSelect);
		displayDriveStatus();
	}
		
	private void killDrive() {
		if( !driveOn )
			return;
		driveOn = false;
		displayDriveStatus();
		if( !driveWrite )
			return;
		driveWrite = false;
		saveImage(driveSelect);
	}

	@Override
	public byte[] getRom256b(){
		return ROM;
	}
	
	@Override
	public String toString() {
		return "Floppy 5.25\" Disk II Controller";
	}

	@Override
	public SwitchSet8 getSwitchSet() {
		return switchSet;
	}

}
