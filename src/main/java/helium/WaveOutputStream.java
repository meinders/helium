/*
 * Copyright 2013-2019 Gerrit Meinders
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package helium;

import java.io.*;
import javax.sound.sampled.*;

public class WaveOutputStream
	extends FilterOutputStream
{
	private final AudioFormat format;

	private boolean headerWritten;

	public WaveOutputStream( final OutputStream out, final AudioFormat format )
	{
		super( out );
		this.format = format;
	}

	private static void writeShortLE( final OutputStream out, final int s )
		throws IOException
	{
		out.write( s );
		out.write( s >> 8 );
	}

	private static void writeIntLE( final OutputStream out, final int i )
		throws IOException
	{
		out.write( i );
		out.write( i >> 8 );
		out.write( i >> 16 );
		out.write( i >> 24 );
	}

	@Override
	public void write( final int b )
		throws IOException
	{
		if ( !headerWritten )
		{
			writeHeader();
		}
		out.write( b );
	}

	@Override
	public void write( final byte[] b, final int off, final int len )
		throws IOException
	{
		if ( !headerWritten )
		{
			writeHeader();
		}
		out.write( b, off, len );
	}

	private void writeHeader()
		throws IOException
	{
		out.write( 'R' );
		out.write( 'I' );
		out.write( 'F' );
		out.write( 'F' );

		// file size (counting from the next byte)
		writeIntLE( out, 0x80000024 ); // unknown size

		out.write( 'W' );
		out.write( 'A' );
		out.write( 'V' );
		out.write( 'E' );

		out.write( 'f' );
		out.write( 'm' );
		out.write( 't' );
		out.write( ' ' );

		final int channels = format.getChannels();
		final int sampleRate = (int)format.getSampleRate();
		final int bitsPerSample = format.getSampleSizeInBits();

		writeIntLE( out, 16 ); // chunk size (counting from the next byte)
		writeShortLE( out, 1 ); // audio format = PCM
		writeShortLE( out, channels ); // number of channels
		writeIntLE( out, sampleRate ); // sample rate
		// byte rate = sample*channels*bits/8
		writeIntLE( out, sampleRate * channels * bitsPerSample / 8 );
		// block align = channels*bits/8
		writeShortLE( out, channels * bitsPerSample / 8 );
		writeShortLE( out, bitsPerSample ); // bits per sample

		out.write( 'd' );
		out.write( 'a' );
		out.write( 't' );
		out.write( 'a' );
		// chunk size (counting from the next byte)
		writeIntLE( out, 0x80000000 ); // unknown size

		headerWritten = true;
	}
}
