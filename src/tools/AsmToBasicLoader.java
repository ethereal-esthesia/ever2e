package tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convert byte-oriented assembly/listing text into an Applesoft BASIC DATA loader.
 *
 * Supported source patterns:
 * - ".byte $A9,$00,96" style directives
 * - "DATA 169,0,96" lines
 * - monitor/listing dumps: "3000: A9 00 60"
 * - standalone hex bytes in comments/lines (best-effort)
 *
 * This does not assemble mnemonics into bytes; source must already contain bytes.
 */
public final class AsmToBasicLoader {
	private static final Pattern DATA_LINE = Pattern.compile("(?i)\\bDATA\\b(.*)$");
	private static final Pattern BYTE_LINE = Pattern.compile("(?i)\\.(byte|db)\\b(.*)$");
	private static final Pattern DUMP_LINE = Pattern.compile("^[\\s]*([0-9A-Fa-f]{4,6}):\\s+(.+)$");
	private static final Pattern HEX_TOKEN = Pattern.compile("(?i)(?:\\$|0x)?([0-9a-f]{1,2})\\b");

	private AsmToBasicLoader() {
	}

	public static void main(String[] args) throws Exception {
		Config cfg = parseArgs(args);
		List<Integer> bytes = extractBytes(cfg.inPath);
		if( bytes.isEmpty() )
			throw new IllegalArgumentException("No byte data found in input: " + cfg.inPath);
		String bas = buildBasicLoader(bytes, cfg.baseAddress, cfg.chunk, cfg.loaderLineStart, cfg.dataLineStart, cfg.dataLineStep);
		Files.writeString(cfg.outPath, bas, StandardCharsets.US_ASCII);
		System.out.println("wrote " + cfg.outPath + " (" + bytes.size() + " bytes, CALL " + cfg.baseAddress + ")");
	}

	private static String buildBasicLoader(List<Integer> bytes, int baseAddress, int chunk, int loaderLineStart, int dataLineStart, int dataLineStep) {
		List<String> out = new ArrayList<>();
		int ln = loaderLineStart;
		out.add(ln + " A=" + baseAddress + ":I=0");
		ln += 10;
		out.add(ln + " READ B:IF B<0 THEN " + (ln + 20));
		ln += 10;
		out.add(ln + " POKE A+I,B:I=I+1:GOTO " + (ln - 10));
		ln += 10;
		out.add(ln + " CALL " + baseAddress);

		List<Integer> data = new ArrayList<>(bytes);
		data.add(-1);
		int line = dataLineStart;
		for( int i = 0; i<data.size(); i += chunk ) {
			StringBuilder sb = new StringBuilder();
			sb.append(line).append(" DATA ");
			for( int j = i; j<Math.min(i + chunk, data.size()); j++ ) {
				if( j>i )
					sb.append(',');
				sb.append(data.get(j));
			}
			out.add(sb.toString());
			line += dataLineStep;
		}
		return String.join("\n", out) + "\n";
	}

	private static List<Integer> extractBytes(Path inPath) throws IOException {
		List<Integer> out = new ArrayList<>();
		for( String raw : Files.readAllLines(inPath, StandardCharsets.UTF_8) ) {
			String line = stripComment(raw);
			if( line.isBlank() )
				continue;

			Matcher mData = DATA_LINE.matcher(line);
			if( mData.find() ) {
				parseNumberList(mData.group(1), out, true);
				continue;
			}
			Matcher mByte = BYTE_LINE.matcher(line);
			if( mByte.find() ) {
				parseNumberList(mByte.group(2), out, true);
				continue;
			}
			Matcher mDump = DUMP_LINE.matcher(line);
			if( mDump.find() ) {
				parseHexBytesOnly(mDump.group(2), out);
				continue;
			}
			// Intentionally no generic fallback parsing: avoid accidentally treating
			// line numbers/comments/text as byte streams.
		}
		return out;
	}

	private static void parseNumberList(String tail, List<Integer> out, boolean allowDecimal) {
		for( String tok : tail.split(",") ) {
			String t = tok.trim();
			if( t.isEmpty() )
				continue;
			Integer v = parseByteToken(t, allowDecimal);
			if( v!=null )
				out.add(v);
		}
	}

	private static void parseHexBytesOnly(String text, List<Integer> out) {
		Matcher m = HEX_TOKEN.matcher(text);
		while( m.find() ) {
			String token = m.group(1);
			int v = Integer.parseInt(token, 16) & 0xFF;
			out.add(v);
		}
	}

	private static Integer parseByteToken(String token, boolean allowDecimal) {
		String t = token.trim().toLowerCase(Locale.ROOT);
		try {
			if( t.startsWith("$") )
				return Integer.parseInt(t.substring(1), 16) & 0xFF;
			if( t.startsWith("0x") )
				return Integer.parseInt(t.substring(2), 16) & 0xFF;
			if( allowDecimal && t.matches("[0-9]+") )
				return Integer.parseInt(t) & 0xFF;
			if( t.matches("[0-9a-f]{1,2}") )
				return Integer.parseInt(t, 16) & 0xFF;
		}
		catch( NumberFormatException ignored ) {
		}
		return null;
	}

	private static String stripComment(String line) {
		int semi = line.indexOf(';');
		int dblSlash = line.indexOf("//");
		int cut = -1;
		if( semi>=0 )
			cut = semi;
		if( dblSlash>=0 && (cut<0 || dblSlash<cut) )
			cut = dblSlash;
		return cut>=0 ? line.substring(0, cut) : line;
	}

	private static Config parseArgs(String[] args) {
		Path in = null;
		Path out = null;
		int base = 0x3000;
		int chunk = 16;
		int loaderLineStart = 10;
		int dataLineStart = 100;
		int dataLineStep = 10;

		for( int i = 0; i<args.length; i++ ) {
			String a = args[i];
			if( "--in".equals(a) && i + 1<args.length )
				in = Path.of(args[++i]);
			else if( "--out".equals(a) && i + 1<args.length )
				out = Path.of(args[++i]);
			else if( "--base".equals(a) && i + 1<args.length )
				base = parseIntAuto(args[++i]);
			else if( "--chunk".equals(a) && i + 1<args.length )
				chunk = Integer.parseInt(args[++i]);
			else if( "--loader-line-start".equals(a) && i + 1<args.length )
				loaderLineStart = Integer.parseInt(args[++i]);
			else if( "--data-line-start".equals(a) && i + 1<args.length )
				dataLineStart = Integer.parseInt(args[++i]);
			else if( "--data-line-step".equals(a) && i + 1<args.length )
				dataLineStep = Integer.parseInt(args[++i]);
			else if( "--help".equals(a) || "-h".equals(a) ) {
				printUsageAndExit(0);
			}
			else {
				System.err.println("Unknown arg: " + a);
				printUsageAndExit(2);
			}
		}

		if( in==null || out==null ) {
			System.err.println("Missing required args --in and/or --out");
			printUsageAndExit(2);
		}
		if( chunk<=0 )
			throw new IllegalArgumentException("--chunk must be > 0");
		return new Config(in, out, base, chunk, loaderLineStart, dataLineStart, dataLineStep);
	}

	private static int parseIntAuto(String s) {
		String t = s.trim().toLowerCase(Locale.ROOT);
		if( t.startsWith("0x") )
			return Integer.parseInt(t.substring(2), 16);
		if( t.startsWith("$") )
			return Integer.parseInt(t.substring(1), 16);
		return Integer.parseInt(t);
	}

	private static void printUsageAndExit(int code) {
		System.out.println("Usage:");
		System.out.println("  gradle --no-daemon runAsmToBasicLoader --args=\"--in <file.asm> --out <file.bas> [options]\"");
		System.out.println();
		System.out.println("Options:");
		System.out.println("  --base <addr>              Loader base/CALL address (default 0x3000)");
		System.out.println("  --chunk <n>                Bytes per BASIC DATA line (default 16)");
		System.out.println("  --loader-line-start <n>    First loader line (default 10)");
		System.out.println("  --data-line-start <n>      First DATA line (default 100)");
		System.out.println("  --data-line-step <n>       DATA line increment (default 10)");
		System.exit(code);
	}

	private record Config(
			Path inPath,
			Path outPath,
			int baseAddress,
			int chunk,
			int loaderLineStart,
			int dataLineStart,
			int dataLineStep
	) {
	}
}
