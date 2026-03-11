package core.memory.memory8;

import peripherals.PeripheralIIe;
import core.exception.HardwareException;
import device.display.DisplayIIe;
import device.display.VideoSignalSource;
import device.keyboard.KeyboardIIe;

public class MemoryBusIIe extends MemoryBus8 {

	public static final int BANKED_RAM = 0x10000;
	public static final int ROM_START = 0xc000;

	private byte[] rom16k;
	private byte[] slotRom[] = new byte[8][];
	private SwitchSet8[] slotSwitchList = new SwitchSet8[8];

	private KeyboardIIe keyboard;
	private VideoSignalSource monitor;

	private SwitchState switch80Store = new SwitchState();
	private SwitchState switchHiRes = new SwitchState();
	private SwitchState switchRamRead = new SwitchState();
	private SwitchState switchRamWrt = new SwitchState();
	private SwitchState switchText = new SwitchState();
	private SwitchState switchPage2 = new SwitchState();
	private SwitchState switchMixed = new SwitchState();
	private SwitchState switchAltZp = new SwitchState();
	private SwitchState switchBank1 = new SwitchState();
	private SwitchState switchHRamRd = new SwitchState();
	private SwitchState switchHRamWrt = new SwitchState();
	private SwitchState switchPreWrite = new SwitchState();
	private SwitchState switchIntCxRom = new SwitchState();
	private SwitchState switchSlotC3Rom = new SwitchState();
	private SwitchState switchIntC8Rom = new SwitchState();
	private SwitchState switch80Col = new SwitchState();
	private SwitchState switchAltCharSet = new SwitchState();
	private SwitchState switchAn0 = new SwitchState();
	private SwitchState switchAn1 = new SwitchState();
	private SwitchState switchAn2 = new SwitchState();
	private SwitchState switchAn3 = new SwitchState();
	private SwitchState switchSpeakerToggle = new SwitchState();

	private int switchIteration;
	// Default PB2 low so boot does not enter self-test paths unintentionally.
	// Game-input devices can drive this high when modeled.
	private boolean pb2DefaultHigh = false;

	public class SwitchState {

		private boolean state = false;

		public boolean getState() {
			return state;
		}

		public void setState() {
			state = true;
			switchIteration++;
		}

		public void resetState() {
			state = false;
			switchIteration++;
		}

		public String toString() {
			return new Boolean(state).toString();
		}

	}

	public interface MemoryAction8 {

		public int readMem( int address );

		public void writeMem( int address, int value );

	}

	public interface SwitchSet8 extends MemoryAction8 {

		public void warmReset();

	}

	public abstract class SwitchIIe implements MemoryAction8 {

		protected SwitchState readSwitchStatus;
		protected SwitchState writeSwitchStatus;

		public SwitchIIe( SwitchState switchStatus ) {
			this.readSwitchStatus = switchStatus;
			this.writeSwitchStatus = switchStatus;
		}

		public SwitchIIe( SwitchState readSwitchStatus, SwitchState writeSwitchStatus ) {
			this.readSwitchStatus = readSwitchStatus;
			this.writeSwitchStatus = writeSwitchStatus;
		}

		@Override
		public int readMem( int address ) {
			if( readSwitchStatus==null )
				return monitor==null ? 0:monitor.getLastRead();
			else
				return (monitor==null ? 0:monitor.getLastRead()) |
						(readSwitchStatus.getState() ? 0x80:0x00);
		}

	}

	private SwitchIIe nullSwitch = new SwitchIIe(null) {
		@Override
		public void writeMem( int address, int value ) {
		}
	};
	
	private SwitchIIe cassetteInSwitch = new SwitchIIe(null) {
		@Override
		public int readMem( int address ) {
			// Apple IIe cassette input softswitch defaults high on bit 7 when the
			// input line is idle; preserve floating-bus bits 0-6.
			return (super.readMem(address)&0x7f) | 0x80;
		}
		@Override
		public void writeMem( int address, int value ) {
		}
	};

	private SwitchIIe paddleTimerSwitch = new SwitchIIe(null) {
		@Override
		public int readMem(int address) {
			// With no active paddle countdown, Apple IIe reads return bit 7 high.
			// Keep floating-bus bits 0-6 to match MAME readback shape.
			return (super.readMem(address)&0x7f) | 0x80;
		}

		@Override
		public void writeMem(int address, int value) {
		}
	};
	
	private SwitchReadOnlyIIe warnSwitch = new SwitchReadOnlyIIe(null) {
		public void writeMem(int address, int value) { System.err.println("Warning: writes not implemented for switch at 0x"+Integer.toHexString(address)); }
		public int readMem(int address) { System.err.println("Warning: reads not implemented for switch at 0x"+Integer.toHexString(address));
		return super.readMem(address); }  };

	public class SwitchSetStatusIIe extends SwitchIIe {

		public SwitchSetStatusIIe( SwitchState switchStatus ) {
			super(switchStatus);
		}

		public SwitchSetStatusIIe( SwitchState readSwitchStatus, SwitchState writeSwithStatus ) {
			super(readSwitchStatus, writeSwithStatus);
		}

		@Override
		public void writeMem(int address, int value) {
			writeSwitchStatus.setState();
		}

	}

	public class SwitchClearStatusIIe extends SwitchIIe {

		public SwitchClearStatusIIe( SwitchState switch80Store ) {
			super(switch80Store);
		}

		public SwitchClearStatusIIe( SwitchState readSwitchStatus, SwitchState writeSwithStatus ) {
			super(readSwitchStatus, writeSwithStatus);
		}

		@Override
		public void writeMem(int address, int value) {
			writeSwitchStatus.resetState();
		}

	}

	public class SwitchClearRWIIe extends SwitchClearOnlyIIe {

		public SwitchClearRWIIe( SwitchState switchStatus ) {
			super(switchStatus);
		}

		@Override
		public int readMem( int address ) {
			// Match Apple IIe/MAME behavior for read/write soft-switches:
			// floating-bus value is sampled before the switch side-effect is applied.
			int value = super.readMem(address);
			writeMem(address, 0x00);
			return value;
		}

	}

	public class SwitchSetRWIIe extends SwitchSetOnlyIIe {

		public SwitchSetRWIIe( SwitchState switchStatus ) {
			super(switchStatus);
		}

		@Override
		public int readMem( int address ) {
			// Match Apple IIe/MAME behavior for read/write soft-switches:
			// floating-bus value is sampled before the switch side-effect is applied.
			int value = super.readMem(address);
			writeMem(address, 0x00);
			return value;
		}

	}

	public class SwitchClearOnlyIIe extends SwitchIIe {

		public SwitchClearOnlyIIe( SwitchState switchStatus ) {
			super(null, switchStatus);
		}

		@Override
		public void writeMem(int address, int value) {
			writeSwitchStatus.resetState();
		}

	}

	public class SwitchSetOnlyIIe extends SwitchIIe {

		public SwitchSetOnlyIIe( SwitchState switchStatus ) {
			super(null, switchStatus);
		}

		@Override
		public void writeMem(int address, int value) {
			writeSwitchStatus.setState();
		}

	}

	public class SwitchReadOnlyIIe extends SwitchIIe {

		public SwitchReadOnlyIIe( SwitchState switchStatus ) {
			super(switchStatus, null);
		}

		@Override
		public void writeMem(int address, int value) {
		}

	}

	private SwitchIIe switchIo_c080_c084 = new SwitchClearStatusIIe(null, switchPreWrite) {

		@Override
		public int readMem( int address ) {
			switchBank1.resetState();
			switchHRamRd.setState();
			switchPreWrite.resetState();
			switchHRamWrt.resetState();
			return super.readMem(address);

		}
		@Override
		public void writeMem(int address, int value) {
			switchBank1.resetState();
			switchHRamRd.setState();
			switchPreWrite.resetState();
			switchHRamWrt.resetState();
		}

	};

	private SwitchIIe switchIo_c081_c085 = new SwitchClearStatusIIe(null, switchPreWrite) {

		@Override
		public int readMem( int address ) {
			switchBank1.resetState();
			switchHRamRd.resetState();
			if( switchPreWrite.getState() )
				switchHRamWrt.setState();
			else
				switchPreWrite.setState();
			return super.readMem(address);

		}
		@Override
		public void writeMem(int address, int value) {
			switchBank1.resetState();
			switchHRamRd.resetState();
			if( switchPreWrite.getState() )
				switchHRamWrt.setState();
			else
				switchPreWrite.setState();
		}

	};

	private SwitchIIe switchIo_c082_c086 = new SwitchClearStatusIIe(null, switchPreWrite) {

		@Override
		public int readMem( int address ) {
			switchBank1.resetState();
			switchHRamRd.resetState();
			switchPreWrite.resetState();
			switchHRamWrt.resetState();
			return super.readMem(address);

		}
		@Override
		public void writeMem(int address, int value) {
			switchBank1.resetState();
			switchHRamRd.resetState();
			switchPreWrite.resetState();
			switchHRamWrt.resetState();
		}

	};

	private SwitchIIe switchIo_c083_c087 = new SwitchClearStatusIIe(null, switchPreWrite) {

		@Override
		public int readMem( int address ) {
			switchBank1.resetState();
			switchHRamRd.setState();
			if( switchPreWrite.getState() )
				switchHRamWrt.setState();
			else
				switchPreWrite.setState();
			return super.readMem(address);

		}
		@Override
		public void writeMem(int address, int value) {
			switchBank1.resetState();
			switchHRamRd.setState();
			if( switchPreWrite.getState() )
				switchHRamWrt.setState();
			else
				switchPreWrite.setState();
		}

	};

	private SwitchIIe switchIo_c088_c08c = new SwitchClearStatusIIe(null, switchPreWrite) {

		@Override
		public int readMem( int address ) {
			switchBank1.setState();
			switchHRamRd.setState();
			switchPreWrite.resetState();
			switchHRamWrt.resetState();
			return super.readMem(address);

		}
		@Override
		public void writeMem(int address, int value) {
			switchBank1.setState();
			switchHRamRd.setState();
			switchPreWrite.resetState();
			switchHRamWrt.resetState();
		}

	};

	private SwitchIIe switchIo_c089_c08d = new SwitchClearStatusIIe(null, switchPreWrite) {

		@Override
		public int readMem( int address ) {
			switchBank1.setState();
			switchHRamRd.resetState();
			if( switchPreWrite.getState() )
				switchHRamWrt.setState();
			else
				switchPreWrite.setState();
			return super.readMem(address);

		}
		@Override
		public void writeMem(int address, int value) {
			switchBank1.setState();
			switchHRamRd.resetState();
			if( switchPreWrite.getState() )
				switchHRamWrt.setState();
			else
				switchPreWrite.setState();
		}

	};

	private SwitchIIe switchIo_c08a_c08e = new SwitchClearStatusIIe(null, switchPreWrite) {

		@Override
		public int readMem( int address ) {
			switchBank1.setState();
			switchHRamRd.resetState();
			switchPreWrite.resetState();
			switchHRamWrt.resetState();
			return super.readMem(address);

		}
		@Override
		public void writeMem(int address, int value) {
			switchBank1.setState();
			switchHRamRd.resetState();
			switchPreWrite.resetState();
			switchHRamWrt.resetState();
		}

	};

	private SwitchIIe switchIo_c08b_c08f = new SwitchClearStatusIIe(null, switchPreWrite) {

		@Override
		public int readMem( int address ) {
			switchBank1.setState();
			switchHRamRd.setState();
			if( switchPreWrite.getState() )
				switchHRamWrt.setState();
			else
				switchPreWrite.setState();
			return super.readMem(address);

		}
		@Override
		public void writeMem(int address, int value) {
			switchBank1.setState();
			switchHRamRd.setState();
			if( switchPreWrite.getState() )
				switchHRamWrt.setState();
			else
				switchPreWrite.setState();
		}

	};

	// C000h - KEYBOARD / 80STORE
	// Read Bit 7 - key pressed
	// Read Bit 0-6 - ASCII code
	private class SwitchKeyboardStrobe extends SwitchReadOnlyIIe {

		public SwitchKeyboardStrobe(SwitchState switchStatus) {
			super(switchStatus);
		}

		@Override
		public int readMem( int address ) {
			if( keyboard==null )
				return 0;
			if( readSwitchStatus==null ) {
				keyboard.toggleKeyQueue(true);
				return keyboard.getTypedKeyCode();
			}
			else
				return readSwitchStatus.getState() ?
						0x80|keyboard.getTypedKeyCode():
							0x7f&keyboard.getTypedKeyCode();
		}

		@Override
		public void writeMem( int address, int value ) {
			switch80Store.resetState();
		}

	}

	// C010h - KEYBOARD STROBE
	// Resets key strobe
	// IIe and later
	// Bit 7 indicates key strobe is currently active
	// Bit 0-6 indicate ASCII code
	private class SwitchKeyboardState extends SwitchReadOnlyIIe {

		public SwitchKeyboardState(SwitchState switchStatus) {
			super(switchStatus);
		}

		@Override
		public int readMem( int address ) {
			if( keyboard==null )
				return 0;
			if( readSwitchStatus==null ) {
				keyboard.toggleKeyQueue(false);
				return keyboard.getHeldKeyCode();
			}
			else
				return readSwitchStatus.getState() ?
						0x80|keyboard.getTypedKeyCode():
							0x7f&keyboard.getTypedKeyCode();
		}

		@Override
		public void writeMem( int address, int value ) {
			if( keyboard==null )
				return;
			if( readSwitchStatus==null )
				keyboard.toggleKeyQueue(false);
			keyboard.getHeldKeyCode();
		}

	}
	
	// C019h - VBL
	// Bit 7 indicates whether vertical blanking is active
	// Bit 0-6 indicate ASCII code
	private class SwitchVblState extends SwitchIIe {

		public SwitchVblState() {
			super(null);
		}

		@Override
		public int readMem( int address ) {
			boolean inVblank = false;
			if( monitor!=null ) {
				inVblank = monitor.isVbl();
			}
			// Apple IIe RDVBL/VBLBAR ($C019) reports bit 7 high when not in VBL.
			int vblBar = inVblank ? 0x00:0x80;
			if( keyboard==null )
				return vblBar;
			return vblBar | (keyboard.getTypedKeyCode()&0x7f);
		}

		@Override
		public void writeMem( int address, int value ) {
			if( keyboard==null )
				return;
			keyboard.getHeldKeyCode();
		}

	}

	// C061h / C069h - OPNAPPLE / PB0
	// Bit 7 is used to indicate if open apple or PB0 game button is pressed
	private SwitchIIe stateOpenApple = new SwitchReadOnlyIIe(null) {
		public int readMem( int address ) {
			return (keyboard!=null && keyboard.isAppleKey() /* || joystick.getButton(0) */ ) ?
				super.readMem(address)|0x80 : super.readMem(address)&0x7f; }
	};

	// C062h / C06Ah - CLSAPPLE / PB1
	// Bit 7 is used to indicate if open apple / option key or PB1 game button is pressed
	private SwitchIIe stateOptionKey = new SwitchReadOnlyIIe(null) {
		public int readMem( int address ) {
			return (keyboard!=null && keyboard.isOptionKey() /* || joystick.getButton(1) */ ) ?
					super.readMem(address)|0x80 : super.readMem(address)&0x7f; }
	};

	// C063h / C06Bh - SHIFT / PB2
	// Bit 7 is used to indicate if open apple or PB0 game button is pressed
	private SwitchIIe stateShiftKey = new SwitchReadOnlyIIe(null) {
		public int readMem( int address ) {
			return ((keyboard!=null && keyboard.isShiftKey()) || pb2DefaultHigh /* || joystick.getButton(2) */ ) ?
					super.readMem(address)|0x80 : super.readMem(address)&0x7f; }
	};

	private class MemoryBlock8 implements MemoryAction8 {

		private MemoryAction8 lastBlockAction = null;
		private int lastBlockStart;
		private MemoryAction8 [] actionArray;
		private int blockBitLen;
		private int memoryAddrStart;
		private int maxMemoryAddr;

		public MemoryBlock8( int memoryAddrStart, int memoryAddrEnd, int blockBitLen ) {
			this.maxMemoryAddr = memoryAddrEnd+1;
			actionArray = new MemoryAction8[(maxMemoryAddr-memoryAddrStart)>>blockBitLen];
			this.blockBitLen = blockBitLen;
			this.memoryAddrStart = memoryAddrStart;
			this.lastBlockStart = memoryAddrStart;
		}

		@Override
		public int readMem( int address ) {
			return actionArray[(address-memoryAddrStart)>>blockBitLen].readMem(address);
		}

		@Override
		public void writeMem( int address, int value ) {
			actionArray[(address-memoryAddrStart)>>blockBitLen].writeMem(address, value);
		}

		void assignNextBlock( int blockStart, MemoryAction8 blockAction ) {
			for( int blockAddr = (lastBlockStart-memoryAddrStart)>>blockBitLen;
					blockAddr<(blockStart-memoryAddrStart)>>blockBitLen;
					blockAddr++ )
				actionArray[blockAddr] = lastBlockAction;
			lastBlockAction = blockAction;
			lastBlockStart = blockStart;
		}

		void completeBlock() {
			assignNextBlock(maxMemoryAddr, null);
			lastBlockStart = memoryAddrStart;
		}

		void assignBlock( int blockStart, MemoryAction8 blockAction ) {
			actionArray[(blockStart-memoryAddrStart)>>blockBitLen] = blockAction;
		}

	}

	private class ZeroPageStackAccess implements MemoryAction8 {

		@Override
		public int readMem( int address )
		{
			if( switchAltZp.getState() )
				return memory.getByte(BANKED_RAM|address);
			else
				return memory.getByte(address);
		}

		@Override
		public void writeMem( int address, int value )
		{
			if( switchAltZp.getState() )
				memory.setByte(BANKED_RAM|address, value);
			else
				memory.setByte(address, value);
		}

	}

	private class BankedRamAccess implements MemoryAction8 {

		@Override
		public int readMem( int address ) {

			// Address RAM 0x200-0xbfff

			// Sather 5-25:
			//   If 80STORE is set, RAMRD and RAMWRT do not affect $400-$7FF
			//   If 80STORE and HIRES are both set, RAMRD and RAMWRT do not affect $400-$7FF or $2000-$3FFF
			// Otherwise the PAGE2 flag is used to indicate auxiliary memory should be used (Sather 5-7, 5-22)

			boolean auxRead;
			address &= 0xffff;
			if( switch80Store.getState() ) {
				if( ( address>=0x400 && address<0x800 ) || ( switchHiRes.getState() && address>=0x2000 && address<0x4000 ) )
					auxRead = switchPage2.getState();
				else
					auxRead = switchRamRead.getState();
			}
			else
				auxRead = switchRamRead.getState();

			if( auxRead )
				return memory.getByte(BANKED_RAM|address);
			else
				return memory.getByte(address);
		}

		@Override
		public void writeMem(int address, int value) {

			// Address RAM 0x200-0xbfff

			// Sather 5-25:
			//   If 80STORE is set, RAMRD and RAMWRT do not affect $400-$7FF
			//   If 80STORE and HIRES are both set, RAMRD and RAMWRT do not affect $400-$7FF or $2000-$3FFF
			// Otherwise the PAGE2 flag is used to indicate auxiliary memory should be used (Sather 5-7, 5-22)

			boolean auxWrite;
			if( switch80Store.getState() ) {
				if( ( address>=0x400 && address<0x800 ) || ( switchHiRes.getState() && address>=0x2000 && address<0x4000 ) )
					auxWrite = switchPage2.getState();
				else
					auxWrite = switchRamWrt.getState();
			}
			else
				auxWrite = switchRamWrt.getState();

			if( auxWrite )
				memory.setByte(BANKED_RAM|address, value);
			else
				memory.setByte(address, value);

		}

	}

	// C100h-C2FFh & C400h-C7FFh System ROM / Peripheral ROM
	private MemoryAction8 slotIoAccess = new MemoryAction8() {

		@Override
		public int readMem( int address )
		{

			int slot = (address-0xc000)>>8;

			if( switchIntCxRom.getState() )
				// Internal ROM at $CNXX
				return Byte.toUnsignedInt(rom16k[address-0xc000]);
			else {
				// Peripheral card ROM at $CNXX
				if( slotRom[slot] != null )
					return Byte.toUnsignedInt(slotRom[slot][address&0x00ff]);
				else
					return monitor==null ? 0:monitor.getLastRead();
			}

		}

		@Override
		public void writeMem(int address, int value) {
			// NOP
			readMem(address);	/// TODO: Verify on hardware
		}

	};

	// C300h-C3FFh System ROM / Peripheral ROM
	private MemoryAction8 slot3IoAccess = new MemoryAction8() {

		@Override
		public int readMem( int address )
		{

			// $C3XX

			// Sather 5-28
			// INTC8ROM - Set by access to $C3XX with SLOTC3ROM reset
			//            Reset by access to $CFFF or 'RESET
			if( !switchSlotC3Rom.getState() )
				switchIntC8Rom.setState();

			if( switchIntCxRom.getState() || !switchSlotC3Rom.getState() ) {
				// Internal ROM at $C3XX
				return Byte.toUnsignedInt(rom16k[address-0xc000]);
			}
			else {
				// Peripheral card ROM at $C3XX
				if( slotRom[3] != null )
					return Byte.toUnsignedInt(slotRom[3][address&0x00ff]);
				else
					return monitor==null ? 0:monitor.getLastRead();
			}

		}

		@Override
		public void writeMem(int address, int value) {
			// NOP
			readMem(address);	/// TODO: Verify on hardware
		}

	};

	// C800h-CFFFh System ROM / Peripheral expansion ROM
	private class ExpansionRomAccess implements MemoryAction8 {

		@Override
		public int readMem( int address )
		{

			// Read from expansion memory 0xc800-0xcffe / soft-switch 0xcfff
			// Sather 5-28
			// INTC8ROM - Set by access to $C3XX with SLOTC3ROM reset
			//            Reset by access to $CFFF or 'RESET
			//            Grants access to internal ROM at $C800-$CFFF

			if( address==0xcfff ) {
				switchIntC8Rom.resetState();
				return monitor==null ? 0:monitor.getLastRead();
			}

			if( switchIntC8Rom.getState() || switchIntCxRom.getState() )
				return Byte.toUnsignedInt(rom16k[address-0xc000]);

			/// STUB ///

			System.err.println("Warning: unsupported read from expansion memory at 0x" +
					Integer.toHexString(address));
			return monitor==null ? 0:monitor.getLastRead();

		/*

			/// Reading locations 0xcN00-0xcNff will enable the block designated to slot N for reading
			/// Reading 0xcfff disables reading 0xc800-0xcfff for all cards and instead directs reads to the system ROM

			else {
				if( false )//// STUB - LC ROM switch on
					return Byte.toUnsignedInt(rom16k.getByte(address-0xc000));
				else {
					if( false )//// internal slot 3 test logic
						return expRom2k[0][address&0x7ff];  // Internal slot 3 ROM
					else
						/// STUB - N should be current slot pointer or ??? for no slot selected
						return expRom2k[N][address&0x07ff];
				}
			}
		*/
		}

		@Override
		public void writeMem(int address, int value) {
			// NOP
			readMem(address);	/// Verify on hardware
		}

	}


	/**
	 * $D000-$FFFF - Upper Memory
	 * See Sather 5-12
	 */
	private class UpperMemoryAccess implements MemoryAction8 {

		@Override
		public int readMem( int address )
		{

			if( switchHRamRd.getState() ) {

				if( switchAltZp.getState() ) {
					if( address<0xe000 && switchBank1.getState() )
						return memory.getByte(BANKED_RAM|(address-0x1000));
					else
						return memory.getByte(BANKED_RAM|address);
				}
				else {
					if( address<0xe000 && switchBank1.getState() )
						return memory.getByte(address-0x1000);
					else
						return memory.getByte(address);
				}

			}
			else
				return Byte.toUnsignedInt(rom16k[address-ROM_START]);

		}

		@Override
		public void writeMem( int address, int value )
		{

			// $D000-$DFFF
			// Sather 5-12

			// Write banked RAM or ignore write to system ROM

			if( switchHRamWrt.getState() ) {

				if( switchAltZp.getState() ) {
					if( address<0xe000 && switchBank1.getState() )
						memory.setByte(BANKED_RAM|(address-0x1000), value);
					else
						memory.setByte(BANKED_RAM|address, value);
				}
				else {
					if( address<0xe000 && switchBank1.getState() )
						memory.setByte(address-0x1000, value);
					else
						memory.setByte(address, value);
				}

			}

		}

	}

	private MemoryBlock8 ioSwitches;

	private MemoryBlock8 memoryLayout;

	public MemoryBusIIe( Memory8 memory, byte [] rom16k ) {
		super(memory);
		
		this.rom16k = rom16k;
		
		memoryLayout = new MemoryBlock8(0x0000, 0xffff, 8);

		// 0000h Zero-page
		// 0100h Stack
		memoryLayout.assignNextBlock(0x0000, new ZeroPageStackAccess());

		// 0200h RAM
		// 0400h Text and low-resolution graphics RAM page 1
		// 0800h Text and low-resolution graphics RAM page 2
		// 0C00h RAM
		// 2000h High-resolution graphics RAM page 1
		// 4000h High-resolution graphics RAM page 2
		// 6000h RAM
		memoryLayout.assignNextBlock(0x0200, new BankedRamAccess());

		// C000h I/O switches
		// See Sather 2-13
		//SwitchIIe nullSwitch = new SwitchReadOnlyIIe(new SwitchState());
		ioSwitches = new MemoryBlock8(0xc000, 0xc0ff, 0) {
			@Override
			public void writeMem(int address, int value) {
				switchIteration++;
				super.writeMem(address, value);
			}
		};
		MemoryBlock8 ioSwitchesOuter = new MemoryBlock8(0xc000, 0xc0ff, 256) {
			public int readMem(int address) {
				System.out.println("R-"+Integer.toHexString(address).toUpperCase());
				try { Thread.sleep(10); } catch (InterruptedException e) { }
				return super.readMem(address);
			}
			public void writeMem(int address, int value) {
				System.out.println("W-"+Integer.toHexString(address).toUpperCase());
				try { Thread.sleep(10); } catch (InterruptedException e) { }
				super.writeMem(address, value);
			}
		};
		ioSwitchesOuter.assignNextBlock(0xc000, ioSwitches);
		ioSwitchesOuter.completeBlock();
		/// TODO: replace this warning with nullSwitch
		ioSwitches.assignNextBlock(0xc000, warnSwitch);
		ioSwitches.completeBlock();
		// TODO: c000 - c000F actually reads keyboard not monitor
		ioSwitches.assignBlock(0xc000, new SwitchKeyboardStrobe(null));
		ioSwitches.assignBlock(0xc001, new SwitchSetOnlyIIe(switch80Store));
		ioSwitches.assignBlock(0xc002, new SwitchClearOnlyIIe(switchRamRead));
		ioSwitches.assignBlock(0xc003, new SwitchSetOnlyIIe(switchRamRead));
		ioSwitches.assignBlock(0xc004, new SwitchClearOnlyIIe(switchRamWrt));
		ioSwitches.assignBlock(0xc005, new SwitchSetOnlyIIe(switchRamWrt));
		ioSwitches.assignBlock(0xc006, new SwitchClearOnlyIIe(switchIntCxRom));
		ioSwitches.assignBlock(0xc007, new SwitchSetOnlyIIe(switchIntCxRom));
		ioSwitches.assignBlock(0xc008, new SwitchClearOnlyIIe(switchAltZp));
		ioSwitches.assignBlock(0xc009, new SwitchSetOnlyIIe(switchAltZp));
		ioSwitches.assignBlock(0xc00a, new SwitchClearOnlyIIe(switchSlotC3Rom));
		ioSwitches.assignBlock(0xc00b, new SwitchSetOnlyIIe(switchSlotC3Rom));
		ioSwitches.assignBlock(0xc00c, new SwitchClearOnlyIIe(switch80Col));
		ioSwitches.assignBlock(0xc00d, new SwitchSetOnlyIIe(switch80Col));
		ioSwitches.assignBlock(0xc00e, new SwitchClearOnlyIIe(switchAltCharSet));
		ioSwitches.assignBlock(0xc00f, new SwitchSetOnlyIIe(switchAltCharSet));
		ioSwitches.assignBlock(0xc010, new SwitchKeyboardState(null));
		ioSwitches.assignBlock(0xc011, new SwitchKeyboardState(switchBank1) {
			public int readMem(int address) { return 0x80^super.readMem(address); /* Read inverted BANK1 */ }
		});
		ioSwitches.assignBlock(0xc012, new SwitchKeyboardState(switchHRamRd));
		ioSwitches.assignBlock(0xc013, new SwitchKeyboardState(switchRamRead));
		ioSwitches.assignBlock(0xc014, new SwitchKeyboardState(switchRamWrt));
		ioSwitches.assignBlock(0xc015, new SwitchKeyboardState(switchIntCxRom));
		ioSwitches.assignBlock(0xc016, new SwitchKeyboardState(switchAltZp));
		ioSwitches.assignBlock(0xc017, new SwitchKeyboardState(switchSlotC3Rom));
		ioSwitches.assignBlock(0xc018, new SwitchKeyboardState(switch80Store));
		ioSwitches.assignBlock(0xc019, new SwitchVblState());
		ioSwitches.assignBlock(0xc01a, new SwitchKeyboardState(switchText));
		ioSwitches.assignBlock(0xc01b, new SwitchKeyboardState(switchMixed));
		ioSwitches.assignBlock(0xc01c, new SwitchKeyboardState(switchPage2));
		ioSwitches.assignBlock(0xc01d, new SwitchKeyboardState(switchHiRes));
		ioSwitches.assignBlock(0xc01e, new SwitchKeyboardState(switchAltCharSet));
		ioSwitches.assignBlock(0xc01f, new SwitchKeyboardState(switch80Col));
		// c020-c02f - most likely cassette toggle
		ioSwitches.assignBlock(0xc030, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc031, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc032, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc033, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc034, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc035, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc036, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc037, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc038, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc039, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc03a, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc03b, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc03c, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc03d, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc03e, new SwitchSetRWIIe(switchSpeakerToggle));
		ioSwitches.assignBlock(0xc03f, new SwitchSetRWIIe(switchSpeakerToggle));
		// c040-c04f - most likely game strobe
		ioSwitches.assignBlock(0xc050, new SwitchClearRWIIe(switchText));
		ioSwitches.assignBlock(0xc051, new SwitchSetRWIIe(switchText));
		ioSwitches.assignBlock(0xc052, new SwitchClearRWIIe(switchMixed));
		ioSwitches.assignBlock(0xc053, new SwitchSetRWIIe(switchMixed));
		ioSwitches.assignBlock(0xc054, new SwitchClearRWIIe(switchPage2));
		ioSwitches.assignBlock(0xc055, new SwitchSetRWIIe(switchPage2));
		ioSwitches.assignBlock(0xc056, new SwitchClearRWIIe(switchHiRes));
		ioSwitches.assignBlock(0xc057, new SwitchSetRWIIe(switchHiRes));
		ioSwitches.assignBlock(0xc058, new SwitchClearRWIIe(switchAn0));
		ioSwitches.assignBlock(0xc059, new SwitchSetRWIIe(switchAn0));
		ioSwitches.assignBlock(0xc05a, new SwitchClearRWIIe(switchAn1));
		ioSwitches.assignBlock(0xc05b, new SwitchSetRWIIe(switchAn1));
		ioSwitches.assignBlock(0xc05c, new SwitchClearRWIIe(switchAn2));
		ioSwitches.assignBlock(0xc05d, new SwitchSetRWIIe(switchAn2));
		ioSwitches.assignBlock(0xc05e, new SwitchClearRWIIe(switchAn3));
		ioSwitches.assignBlock(0xc05f, new SwitchSetRWIIe(switchAn3));
		// c060 - cassette in
		ioSwitches.assignBlock(0xc060, cassetteInSwitch);
		ioSwitches.assignBlock(0xc061, stateOpenApple);
		ioSwitches.assignBlock(0xc062, stateOptionKey);
		ioSwitches.assignBlock(0xc063, stateShiftKey);
		// c064 PADDL0 Analog Input 0
		// c065 PADDL1 Analog Input 1
		// c066 PADDL2 Analog Input 2
		// c067 PADDL3 Analog Input 3
		ioSwitches.assignBlock(0xc064, paddleTimerSwitch);
		ioSwitches.assignBlock(0xc065, paddleTimerSwitch);
		ioSwitches.assignBlock(0xc066, paddleTimerSwitch);
		ioSwitches.assignBlock(0xc067, paddleTimerSwitch);
		// c068 - cassette in
		ioSwitches.assignBlock(0xc068, cassetteInSwitch);
		ioSwitches.assignBlock(0xc069, stateOpenApple);
		ioSwitches.assignBlock(0xc06a, stateOptionKey);
		ioSwitches.assignBlock(0xc06b, stateShiftKey);
		// c06c PADDL0 Analog Input 0
		// c06d PADDL1 Analog Input 1
		// c06e PADDL2 Analog Input 2
		// c06f PADDL3 Analog Input 3
		ioSwitches.assignBlock(0xc06c, paddleTimerSwitch);
		ioSwitches.assignBlock(0xc06d, paddleTimerSwitch);
		ioSwitches.assignBlock(0xc06e, paddleTimerSwitch);
		ioSwitches.assignBlock(0xc06f, paddleTimerSwitch);
		// c070 - Paddle strobe, likely - c07f as well
		ioSwitches.assignBlock(0xc080, switchIo_c080_c084);
		ioSwitches.assignBlock(0xc081, switchIo_c081_c085);
		ioSwitches.assignBlock(0xc082, switchIo_c082_c086);
		ioSwitches.assignBlock(0xc083, switchIo_c083_c087);
		ioSwitches.assignBlock(0xc084, switchIo_c080_c084);
		ioSwitches.assignBlock(0xc085, switchIo_c081_c085);
		ioSwitches.assignBlock(0xc086, switchIo_c082_c086);
		ioSwitches.assignBlock(0xc087, switchIo_c083_c087);
		ioSwitches.assignBlock(0xc088, switchIo_c088_c08c);
		ioSwitches.assignBlock(0xc089, switchIo_c089_c08d);
		ioSwitches.assignBlock(0xc08a, switchIo_c08a_c08e);
		ioSwitches.assignBlock(0xc08b, switchIo_c08b_c08f);
		ioSwitches.assignBlock(0xc08c, switchIo_c088_c08c);
		ioSwitches.assignBlock(0xc08d, switchIo_c089_c08d);
		ioSwitches.assignBlock(0xc08e, switchIo_c08a_c08e);
		ioSwitches.assignBlock(0xc08f, switchIo_c08b_c08f);

		for( int i = 0xc090; i<0xc100; i++ )
			ioSwitches.assignBlock(i, nullSwitch);

		memoryLayout.assignNextBlock(0xc000, ioSwitches);
		//memoryLayout.assignNextBlock(0xc000, ioSwitchesOuter);

		// C100h System ROM / Peripheral ROM
		memoryLayout.assignNextBlock(0xc100, slotIoAccess);
		memoryLayout.assignNextBlock(0xc200, slotIoAccess);
		memoryLayout.assignNextBlock(0xc300, slot3IoAccess);
		memoryLayout.assignNextBlock(0xc400, slotIoAccess);
		memoryLayout.assignNextBlock(0xc500, slotIoAccess);
		memoryLayout.assignNextBlock(0xc600, slotIoAccess);
		memoryLayout.assignNextBlock(0xc700, slotIoAccess);

		// C800h System ROM / Peripheral expansion ROM
		memoryLayout.assignNextBlock(0xc800, new ExpansionRomAccess());

		// D000h System ROM / RAM / banked RAM
		memoryLayout.assignNextBlock(0xd000, new UpperMemoryAccess());

		memoryLayout.completeBlock();

	}

	@Override
	public int getByte( int address ) {
		return memoryLayout.readMem(address);
	}

	public int peekByteNoSideEffects(int address) {
		address &= 0xffff;
		if( address<0x0200 ) {
			if( switchAltZp.getState() )
				return memory.getByte(BANKED_RAM|address);
			return memory.getByte(address);
		}
		if( address<0xc000 ) {
			boolean auxRead;
			if( switch80Store.getState() ) {
				if( ( address>=0x400 && address<0x800 ) || ( switchHiRes.getState() && address>=0x2000 && address<0x4000 ) )
					auxRead = switchPage2.getState();
				else
					auxRead = switchRamRead.getState();
			}
			else
				auxRead = switchRamRead.getState();
			if( auxRead )
				return memory.getByte(BANKED_RAM|address);
			return memory.getByte(address);
		}
		if( address<0xc100 )
			// Snapshot dumps should avoid side effects, but still expose live status readings.
			switch( address ) {
				case 0xc010:
					return keyboard==null ? 0 : keyboard.getHeldKeyCode();
				case 0xc011: {
					int keyCode = keyboard==null ? 0 : keyboard.getTypedKeyCode();
					int status = switchBank1.getState() ? (0x80|keyCode) : (0x7f&keyCode);
					return 0x80 ^ status; // Inverted BANK1 readback.
				}
				case 0xc012: {
					int keyCode = keyboard==null ? 0 : keyboard.getTypedKeyCode();
					return switchHRamRd.getState() ? (0x80|keyCode) : (0x7f&keyCode);
				}
				case 0xc013: {
					int keyCode = keyboard==null ? 0 : keyboard.getTypedKeyCode();
					return switchRamRead.getState() ? (0x80|keyCode) : (0x7f&keyCode);
				}
				case 0xc014: {
					int keyCode = keyboard==null ? 0 : keyboard.getTypedKeyCode();
					return switchRamWrt.getState() ? (0x80|keyCode) : (0x7f&keyCode);
				}
				case 0xc015: {
					int keyCode = keyboard==null ? 0 : keyboard.getTypedKeyCode();
					return switchIntCxRom.getState() ? (0x80|keyCode) : (0x7f&keyCode);
				}
				case 0xc016: {
					int keyCode = keyboard==null ? 0 : keyboard.getTypedKeyCode();
					return switchAltZp.getState() ? (0x80|keyCode) : (0x7f&keyCode);
				}
				case 0xc017: {
					int keyCode = keyboard==null ? 0 : keyboard.getTypedKeyCode();
					return switchSlotC3Rom.getState() ? (0x80|keyCode) : (0x7f&keyCode);
				}
				case 0xc018: {
					int keyCode = keyboard==null ? 0 : keyboard.getTypedKeyCode();
					return switch80Store.getState() ? (0x80|keyCode) : (0x7f&keyCode);
				}
				case 0xc019: {
					boolean inVblank = false;
					if( monitor!=null )
						inVblank = monitor.isVbl();
					int vblBar = inVblank ? 0x00:0x80;
					if( keyboard==null )
						return vblBar;
					return vblBar | (keyboard.getTypedKeyCode()&0x7f);
				}
				case 0xc01a: {
					int keyCode = keyboard==null ? 0 : keyboard.getTypedKeyCode();
					return switchText.getState() ? (0x80|keyCode) : (0x7f&keyCode);
				}
				case 0xc01b: {
					int keyCode = keyboard==null ? 0 : keyboard.getTypedKeyCode();
					return switchMixed.getState() ? (0x80|keyCode) : (0x7f&keyCode);
				}
				case 0xc01c: {
					int keyCode = keyboard==null ? 0 : keyboard.getTypedKeyCode();
					return switchPage2.getState() ? (0x80|keyCode) : (0x7f&keyCode);
				}
				case 0xc01d: {
					int keyCode = keyboard==null ? 0 : keyboard.getTypedKeyCode();
					return switchHiRes.getState() ? (0x80|keyCode) : (0x7f&keyCode);
				}
				case 0xc01e: {
					int keyCode = keyboard==null ? 0 : keyboard.getTypedKeyCode();
					return switchAltCharSet.getState() ? (0x80|keyCode) : (0x7f&keyCode);
				}
				case 0xc01f: {
					int keyCode = keyboard==null ? 0 : keyboard.getTypedKeyCode();
					return switch80Col.getState() ? (0x80|keyCode) : (0x7f&keyCode);
				}
				case 0xc061:
				case 0xc069: {
					return (keyboard!=null && keyboard.isAppleKey()) ? 0x80 : 0x00;
				}
				case 0xc062:
				case 0xc06a: {
					return (keyboard!=null && keyboard.isOptionKey()) ? 0x80 : 0x00;
				}
				case 0xc063:
				case 0xc06b: {
					return ((keyboard!=null && keyboard.isShiftKey()) || pb2DefaultHigh) ? 0x80 : 0x00;
				}
				case 0xc060:
				case 0xc068: {
					// Mirror default cassette idle behavior used by MAME: high bit set.
					return 0x80;
				}
				case 0xc064:
				case 0xc065:
				case 0xc066:
				case 0xc067:
				case 0xc06c:
				case 0xc06d:
				case 0xc06e:
				case 0xc06f: {
					// With no active paddle timer event, reads default to bit 7 high.
					return 0x80;
				}
				default:
					return 0x00;
			}
		if( address<0xc800 ) {
			int slot = (address-0xc000)>>8;
			if( slot==3 ) {
				if( switchIntCxRom.getState() || !switchSlotC3Rom.getState() )
					return Byte.toUnsignedInt(rom16k[address-0xc000]);
				if( slotRom[3] != null )
					return Byte.toUnsignedInt(slotRom[3][address&0x00ff]);
				return 0x00;
			}
			if( switchIntCxRom.getState() )
				return Byte.toUnsignedInt(rom16k[address-0xc000]);
			if( slotRom[slot] != null )
				return Byte.toUnsignedInt(slotRom[slot][address&0x00ff]);
			return 0x00;
		}
		if( address<0xd000 ) {
			if( address==0xcfff )
				return 0x00;
			if( switchIntC8Rom.getState() || switchIntCxRom.getState() )
				return Byte.toUnsignedInt(rom16k[address-0xc000]);
			return 0x00;
		}
		if( switchHRamRd.getState() ) {
			if( switchAltZp.getState() ) {
				if( address<0xe000 && switchBank1.getState() )
					return memory.getByte(BANKED_RAM|(address-0x1000));
				return memory.getByte(BANKED_RAM|address);
			}
			if( address<0xe000 && switchBank1.getState() )
				return memory.getByte(address-0x1000);
			return memory.getByte(address);
		}
		return Byte.toUnsignedInt(rom16k[address-ROM_START]);
	}

	@Override
	public void setByte( int address, int value ) {
		memoryLayout.writeMem(address, value);
	}

	public void warmReset() {
		// Apple IIe Platinum warm reset defaults display to TEXT + PAGE1.
		switchText.setState();
		switch80Store.resetState();
		switchHiRes.resetState();
		switchRamRead.resetState();
		switchRamWrt.resetState();
		switchAltZp.resetState();
		switchPage2.resetState();
		switchBank1.resetState();
		switchHRamRd.resetState();
		switchHRamWrt.resetState();
		switchPreWrite.resetState();
		switchIntCxRom.resetState();
		switchSlotC3Rom.resetState();
		switchIntC8Rom.resetState();
		switch80Col.resetState();
		switchAltCharSet.resetState();
		switchAn0.resetState();
		switchAn1.resetState();
		switchAn2.resetState();
		switchAn3.resetState();
		switchIteration++;
		for( int i = 0; i<6; i++ ) 
			if( slotSwitchList[i]!=null )
				slotSwitchList[i].warmReset();
	}

	@Override
	public void coldReset() throws HardwareException {

		super.coldReset();
		// Match MAME boot snapshot pattern for main 64K: 00/FF alternating by byte.
		for( int addr = 0; addr<0x10000; addr++ )
			memory.setByte(addr, (addr&0x1)==0 ? 0x00 : 0xff);

		warmReset();

	}

	public PeripheralIIe setSlot( int slot, PeripheralIIe peripheralCard ) {
		
		int blockAddr = 0xc080+(slot<<4);
		for( int k=0; k<16; k++ )
			if( peripheralCard!=null )
				ioSwitches.assignBlock(blockAddr++, peripheralCard.getSwitchSet());
			else
				ioSwitches.assignBlock(blockAddr++, nullSwitch);
		PeripheralIIe card = null;
		if( PeripheralIIe.class.isInstance(peripheralCard) )
			card = (PeripheralIIe) peripheralCard;
		slotSwitchList[slot-1] = card==null ? null:card.getSwitchSet();
		return card;
		
	}

	public void resetSlot( int slot ) {
		setSlot(slot, null);
	}

	public void setSlotRom( int slot, byte[] slotRom ) {
		this.slotRom[slot] = slotRom;
	}

	public KeyboardIIe getKeyboard() {
		return keyboard;
	}

	public void setKeyboard( KeyboardIIe keyboard ) {
		this.keyboard = keyboard;
	}

	public VideoSignalSource getDisplay() {
		return monitor;
	}

	public void setDisplay( VideoSignalSource display ) {
		this.monitor = display;
	}

	public boolean is80Store() {
		return switch80Store.getState();
	}

	public void set80Store( boolean switch80Store ) {
		if( switch80Store )
			this.switch80Store.setState();
		else
			this.switch80Store.resetState();
	}

	public boolean isHiRes() {
		return switchHiRes.getState();
	}

	public void setHiRes( boolean switchHiRes ) {
		if( switchHiRes )
			this.switchHiRes.setState();
		else
			this.switchHiRes.resetState();
	}

	public boolean isRamRead() {
		return switchRamRead.getState();
	}

	public void setRamRead( boolean switchRamRead ) {
		if( switchRamRead )
			this.switchRamRead.setState();
		else
			this.switchRamRead.resetState();
	}

	public boolean isRamWrt() {
		return switchRamWrt.getState();
	}

	public void setRamWrt( boolean switchRamWrt ) {
		if( switchRamWrt )
			this.switchRamWrt.setState();
		else
			this.switchRamWrt.resetState();
	}

	public boolean isText() {
		return switchText.getState();
	}

	public void setText( boolean switchText ) {
		if( switchText )
			this.switchText.setState();
		else
			this.switchText.resetState();
	}

	public boolean isPage2() {
		return switchPage2.getState();
	}

	public void setPage2( boolean switchPage2 ) {
		if( switchPage2 )
			this.switchPage2.setState();
		else
			this.switchPage2.resetState();
	}

	public boolean isMixed() {
		return switchMixed.getState();
	}

	public void setMixed( boolean switchMixed ) {
		if( switchMixed )
			this.switchMixed.setState();
		else
			this.switchMixed.resetState();
	}

	public boolean isAltZp() {
		return switchAltZp.getState();
	}

	public void setAltZp( boolean switchAltZp ) {
		if( switchAltZp )
			this.switchAltZp.setState();
		else
			this.switchAltZp.resetState();
	}

	public boolean isBank1() {
		return switchBank1.getState();
	}

	public void setBank1( boolean switchBank1 ) {
		if( switchBank1 )
			this.switchBank1.setState();
		else
			this.switchBank1.resetState();
	}

	public boolean isHRamRd() {
		return switchHRamRd.getState();
	}

	public void setHRamRd( boolean switchHRamRd ) {
		if( switchHRamRd )
			this.switchHRamRd.setState();
		else
			this.switchHRamRd.resetState();
	}

	public boolean isHRamWrt() {
		return switchHRamWrt.getState();
	}

	public void setHRamWrt( boolean switchHRamWrt ) {
		if( switchHRamWrt )
			this.switchHRamWrt.setState();
		else
			this.switchHRamWrt.resetState();
	}

	public boolean isPreWrite() {
		return switchPreWrite.getState();
	}

	public void setPreWrite( boolean switchPreWrite ) {
		if( switchPreWrite )
			this.switchPreWrite.setState();
		else
			this.switchPreWrite.resetState();
	}

	public boolean isIntCxRom() {
		return switchIntCxRom.getState();
	}

	public void setIntCxRom( boolean switchIntCxRom ) {
		if( switchIntCxRom )
			this.switchIntCxRom.setState();
		else
			this.switchIntCxRom.resetState();
	}

	public boolean isSlotC3Rom() {
		return switchSlotC3Rom.getState();
	}

	public void setSlotC3Rom( boolean switchSlotC3Rom ) {
		if( switchSlotC3Rom )
			this.switchSlotC3Rom.setState();
		else
			this.switchSlotC3Rom.resetState();
	}

	public boolean isIntC8Rom() {
		return switchIntC8Rom.getState();
	}

	public void setIntC8Rom( boolean switchIntC8Rom ) {
		if( switchIntC8Rom )
			this.switchIntC8Rom.setState();
		else
			this.switchIntC8Rom.resetState();
	}

	public boolean is80Col() {
		return switch80Col.getState();
	}

	public void set80Col( boolean switch80Col ) {
		if( switch80Col )
			this.switch80Col.setState();
		else
			this.switch80Col.resetState();
	}

	public boolean isAltCharSet() {
		return switchAltCharSet.getState();
	}

	public void setAltCharSet( boolean switchAltCharSet ) {
		if( switchAltCharSet )
			this.switchAltCharSet.setState();
		else
			this.switchAltCharSet.resetState();
	}

	public boolean isAn0() {
		return switchAn0.getState();
	}

	public void setAn0( boolean switchAn0 ) {
		if( switchAn0 )
			this.switchAn0.setState();
		else
			this.switchAn0.resetState();
	}

	public boolean isAn1() {
		return switchAn1.getState();
	}

	public void setAn1( boolean switchAn1 ) {
		if( switchAn1 )
			this.switchAn1.setState();
		else
			this.switchAn1.resetState();
	}

	public boolean isAn2() {
		return switchAn2.getState();
	}

	public void setAn2( boolean switchAn2 ) {
		if( switchAn2 )
			this.switchAn2.setState();
		else
			this.switchAn2.resetState();
	}

	public boolean isAn3() {
		return switchAn3.getState();
	}

	public void setAn3( boolean switchAn3 ) {
		if( switchAn3 )
			this.switchAn3.setState();
		else
			this.switchAn3.resetState();
	}

	public boolean isSpeakerToggle() {
		return switchSpeakerToggle.getState();
	}

	public void setSpeakerToggle( boolean switchSpeakerToggle ) {
		if( switchSpeakerToggle )
			this.switchSpeakerToggle.setState();
		else
			this.switchSpeakerToggle.resetState();
	}

	public int getSwitchIteration() {
		return switchIteration;
	}

/*

	public String toString()
	{

		// Get/set cout settings
		ios_base::fmtflags coutFlags = cout.flags();
		cout << hex << uppercase << setfill ('0');

		// Read main memory
		putMem(0xc000, 0x00);  // 80STORE off
		putMem(0xc002, 0x00);  // RAMRD off
		putMem(0xc008, 0x00);  // ALTZP off

		for( int page = 0; page<TOTAL_RAM_PAGES; page++ ) {

			cout << "RAM PAGE " << page << endl;

			Uint16 addr = 0x0000;
			for( int b = 0; b<0x100; b++ ) {
				cout << endl;
				cout << "ADDR   0  1  2  3  4  5  6  7  8  9  A  B  C  D  E  F\n";
				for( int l = 0; l<0x10; l++ ) {
					cout << setw(4) << addr << ": ";
					for( int c = 0; c<0x10; c++ )
						cout << setw(2) << (int) ramPage64k[(page<<16)|addr++] << " ";
					cout << endl;
				}
			}

			cout << endl << endl;

		}

		// Restore cout settings
		cout.flags(coutFlags);

	}

*/

}
