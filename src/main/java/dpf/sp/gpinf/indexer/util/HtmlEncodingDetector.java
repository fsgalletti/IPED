package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.tika.metadata.Metadata;

public class HtmlEncodingDetector extends org.apache.tika.parser.html.HtmlEncodingDetector{
	
	private static String utf8Header = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";

	@Override
	public Charset detect(InputStream input, Metadata metadata) throws IOException {
		if(input == null)
			return null;
		
		byte[] header = new byte[1024];
		int read = 0, len = 0;
		input.mark(header.length);
		try{
			while(len < header.length && (read = input.read(header, len, header.length - len)) != -1)
				len += read;
		}finally{
			input.reset();
		}
		
		if(len > 3 && header[0] == (byte)0xEF && header[1] == (byte)0xBB && header[2] == (byte)0xBF){
			String str = new String(header, 3, len - 3, "UTF-8");
			if(str.startsWith(utf8Header))
				return Charset.forName("UTF-8");
		}
		
		return super.detect(input, metadata);
	}

}