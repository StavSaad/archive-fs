package com.github.peterlaker.nio.file.tar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.PatternSyntaxException;

public class TarUtils {

	public static String trim(String s, char c) {
		StringBuffer tmp = new StringBuffer(s);
		for (int i = 0; i < tmp.length(); i++) {
			if (tmp.charAt(i) != c) {
				break;
			} else {
				tmp.deleteCharAt(i);
			}
		}

		for (int i = tmp.length() - 1; i >= 0; i--) {
			if (tmp.charAt(i) != c) {
				break;
			} else {
				tmp.deleteCharAt(i);
			}
		}

		return tmp.toString();
	}

	private static final String regexMetaChars = ".^$+{[]|()";
	private static final String globMetaChars = "\\*?[{";

	private static boolean isRegexMeta(char c) {
		return regexMetaChars.indexOf(c) != -1;
	}

	private static boolean isGlobMeta(char c) {
		return globMetaChars.indexOf(c) != -1;
	}

	private static char EOL = 0; // TBD

	private static char next(String glob, int i) {
		if (i < glob.length()) {
			return glob.charAt(i);
		}
		return EOL;
	}

	public static String toRegexPattern(String globPattern) {
		boolean inGroup = false;
		StringBuilder regex = new StringBuilder("^");

		int i = 0;
		while (i < globPattern.length()) {
			char c = globPattern.charAt(i++);
			switch (c) {
			case '\\':
				// escape special characters
				if (i == globPattern.length()) {
					throw new PatternSyntaxException("No character to escape",
							globPattern, i - 1);
				}
				char next = globPattern.charAt(i++);
				if (isGlobMeta(next) || isRegexMeta(next)) {
					regex.append('\\');
				}
				regex.append(next);
				break;
			case '/':
				regex.append(c);
				break;
			case '[':
				// don't match name separator in class
				regex.append("[[^/]&&[");
				if (next(globPattern, i) == '^') {
					// escape the regex negation char if it appears
					regex.append("\\^");
					i++;
				} else {
					// negation
					if (next(globPattern, i) == '!') {
						regex.append('^');
						i++;
					}
					// hyphen allowed at start
					if (next(globPattern, i) == '-') {
						regex.append('-');
						i++;
					}
				}
				boolean hasRangeStart = false;
				char last = 0;
				while (i < globPattern.length()) {
					c = globPattern.charAt(i++);
					if (c == ']') {
						break;
					}
					if (c == '/') {
						throw new PatternSyntaxException(
								"Explicit 'name separator' in class",
								globPattern, i - 1);
					}
					// TBD: how to specify ']' in a class?
					if (c == '\\' || c == '[' || c == '&'
							&& next(globPattern, i) == '&') {
						// escape '\', '[' or "&&" for regex class
						regex.append('\\');
					}
					regex.append(c);

					if (c == '-') {
						if (!hasRangeStart) {
							throw new PatternSyntaxException("Invalid range",
									globPattern, i - 1);
						}
						if ((c = next(globPattern, i++)) == EOL || c == ']') {
							break;
						}
						if (c < last) {
							throw new PatternSyntaxException("Invalid range",
									globPattern, i - 3);
						}
						regex.append(c);
						hasRangeStart = false;
					} else {
						hasRangeStart = true;
						last = c;
					}
				}
				if (c != ']') {
					throw new PatternSyntaxException("Missing ']", globPattern,
							i - 1);
				}
				regex.append("]]");
				break;
			case '{':
				if (inGroup) {
					throw new PatternSyntaxException("Cannot nest groups",
							globPattern, i - 1);
				}
				regex.append("(?:(?:");
				inGroup = true;
				break;
			case '}':
				if (inGroup) {
					regex.append("))");
					inGroup = false;
				} else {
					regex.append('}');
				}
				break;
			case ',':
				if (inGroup) {
					regex.append(")|(?:");
				} else {
					regex.append(',');
				}
				break;
			case '*':
				if (next(globPattern, i) == '*') {
					// crosses directory boundaries
					regex.append(".*");
					i++;
				} else {
					// within directory boundary
					regex.append("[^/]*");
				}
				break;
			case '?':
				regex.append("[^/]");
				break;
			default:
				if (isRegexMeta(c)) {
					regex.append('\\');
				}
				regex.append(c);
			}
		}
		if (inGroup) {
			throw new PatternSyntaxException("Missing '}", globPattern, i - 1);
		}
		return regex.append('$').toString();
	}

	public static byte[] readAllBytes(InputStream inputStream) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int nRead;
		byte[] data = new byte[16384];
		while((nRead = inputStream.read(data, 0, data.length-1)) != -1) {
			baos.write(data, 0, nRead);
		}
		return baos.toByteArray();
	}

}
