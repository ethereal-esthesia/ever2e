package device.display;

public interface VideoSignalSource {
	int getLastRead();
	int getHScan();
	boolean isVbl();
	int getVScan();
}
